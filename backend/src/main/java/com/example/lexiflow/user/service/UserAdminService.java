package com.example.lexiflow.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.lexiflow.security.CurrentUser;
import com.example.lexiflow.user.mapper.AppUserMapper;
import com.example.lexiflow.user.mapper.PermissionMapper;
import com.example.lexiflow.user.mapper.RoleMapper;
import com.example.lexiflow.user.mapper.UserPermissionMapper;
import com.example.lexiflow.user.mapper.UserRoleAdminMapper;
import com.example.lexiflow.user.model.AppUser;
import com.example.lexiflow.user.model.Permission;
import com.example.lexiflow.user.model.Role;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserAdminService {

    private final AppUserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserPermissionMapper permissionLookup;
    private final UserRoleAdminMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(AppUserMapper userMapper, RoleMapper roleMapper, PermissionMapper permissionMapper,
                            UserPermissionMapper permissionLookup, UserRoleAdminMapper userRoleMapper,
                            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.permissionLookup = permissionLookup;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserSummary> listUsers(CurrentUser operator) {
        requireAdmin(operator);
        return userMapper.selectList(new LambdaQueryWrapper<AppUser>()
                        .eq(AppUser::getDeleted, false)
                        .orderByAsc(AppUser::getId))
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public UserSummary createUser(CreateUserCommand command, CurrentUser operator) {
        requireAdmin(operator);
        AppUser existing = userMapper.selectOne(new LambdaQueryWrapper<AppUser>()
                .eq(AppUser::getUsername, command.username())
                .eq(AppUser::getDeleted, false));
        if (existing != null) {
            throw new IllegalArgumentException("Username already exists.");
        }
        if (!StringUtils.hasText(command.password())) {
            throw new IllegalArgumentException("Password is required.");
        }

        AppUser user = new AppUser();
        user.setUsername(command.username());
        user.setPasswordHash(passwordEncoder.encode(command.password()));
        user.setDisplayName(command.displayName());
        user.setDepartmentId(command.departmentId());
        user.setEnabled(command.enabled() == null || command.enabled());
        user.setCreatedBy(operator.id());
        user.setUpdatedBy(operator.id());
        user.setDeleted(false);
        userMapper.insert(user);
        replaceRoles(user.getId(), command.roles());
        return toSummary(userMapper.selectById(user.getId()));
    }

    @Transactional
    public UserSummary updateUser(Long id, UpdateUserCommand command, CurrentUser operator) {
        requireAdmin(operator);
        AppUser user = requireUser(id);
        if (StringUtils.hasText(command.displayName())) {
            user.setDisplayName(command.displayName());
        }
        user.setDepartmentId(command.departmentId());
        if (command.enabled() != null) {
            user.setEnabled(command.enabled());
        }
        if (StringUtils.hasText(command.password())) {
            user.setPasswordHash(passwordEncoder.encode(command.password()));
        }
        user.setUpdatedBy(operator.id());
        userMapper.updateById(user);
        if (command.roles() != null) {
            replaceRoles(id, command.roles());
        }
        return toSummary(userMapper.selectById(id));
    }

    @Transactional
    public void deleteUser(Long id, CurrentUser operator) {
        requireAdmin(operator);
        if (operator.id().equals(id)) {
            throw new IllegalArgumentException("Cannot delete current user.");
        }
        AppUser user = requireUser(id);
        user.setDeleted(true);
        user.setEnabled(false);
        user.setUpdatedBy(operator.id());
        userMapper.updateById(user);
    }

    public List<RoleSummary> listRoles(CurrentUser operator) {
        requireAdmin(operator);
        return roleMapper.selectList(new LambdaQueryWrapper<Role>()
                        .eq(Role::getDeleted, false)
                        .orderByAsc(Role::getCode))
                .stream()
                .map(role -> new RoleSummary(role.getId(), role.getCode(), role.getName(), permissionLookup.findPermissionCodesByRole(role.getId())))
                .toList();
    }

    public List<PermissionSummary> listPermissions(CurrentUser operator) {
        requireAdmin(operator);
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                        .eq(Permission::getDeleted, false)
                        .orderByAsc(Permission::getCode))
                .stream()
                .map(permission -> new PermissionSummary(permission.getId(), permission.getCode(), permission.getName()))
                .toList();
    }

    private AppUser requireUser(Long id) {
        AppUser user = userMapper.selectById(id);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw new IllegalArgumentException("User not found: " + id);
        }
        return user;
    }

    private void replaceRoles(Long userId, List<String> roles) {
        userRoleMapper.deleteUserRoles(userId);
        if (roles == null) {
            return;
        }
        roles.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .forEach(role -> userRoleMapper.insertUserRole(userId, role));
    }

    private UserSummary toSummary(AppUser user) {
        return new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getDepartmentId(),
                Boolean.TRUE.equals(user.getEnabled()),
                userRoleMapper.findRoleCodes(user.getId()),
                permissionLookup.findPermissionCodes(user.getId()),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private void requireAdmin(CurrentUser user) {
        if (user == null || !user.permissions().contains("admin:manage")) {
            throw new IllegalStateException("Admin permission is required.");
        }
    }

    public record CreateUserCommand(String username, String password, String displayName, Long departmentId,
                                    Boolean enabled, List<String> roles) {
    }

    public record UpdateUserCommand(String password, String displayName, Long departmentId, Boolean enabled,
                                    List<String> roles) {
    }

    public record UserSummary(Long id, String username, String displayName, Long departmentId, boolean enabled,
                              List<String> roles, List<String> permissions,
                              java.time.OffsetDateTime createdAt, java.time.OffsetDateTime updatedAt) {
    }

    public record RoleSummary(Long id, String code, String name, List<String> permissions) {
    }

    public record PermissionSummary(Long id, String code, String name) {
    }
}
