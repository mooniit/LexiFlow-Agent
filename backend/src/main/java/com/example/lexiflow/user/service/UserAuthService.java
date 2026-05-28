package com.example.lexiflow.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.user.mapper.AppUserMapper;
import com.example.lexiflow.user.mapper.UserPermissionMapper;
import com.example.lexiflow.user.model.AppUser;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserAuthService {

    private final AppUserMapper userMapper;
    private final UserPermissionMapper permissionMapper;
    private final PasswordEncoder passwordEncoder;

    public UserAuthService(AppUserMapper userMapper, UserPermissionMapper permissionMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.permissionMapper = permissionMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public CurrentUser authenticate(String username, String password) {
        AppUser user = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, username)
                .eq(AppUser::getDeleted, false));

        if (user == null || Boolean.FALSE.equals(user.getEnabled()) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        List<String> roles = permissionMapper.findRoleCodes(user.getId());
        List<String> permissions = permissionMapper.findPermissionCodes(user.getId());
        return new CurrentUser(user.getId(), user.getUsername(), user.getDisplayName(), roles, permissions, user.getEnabled());
    }
}

