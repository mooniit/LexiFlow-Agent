package com.example.lexiflow.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.lexiflow.user.model.Permission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
