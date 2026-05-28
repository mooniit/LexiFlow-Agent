package com.example.lexiflow.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserPermissionMapper {

    @Select("""
            SELECT r.code
            FROM role r
            JOIN user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.deleted = FALSE
            ORDER BY r.code
            """)
    List<String> findRoleCodes(Long userId);

    @Select("""
            SELECT DISTINCT p.code
            FROM permission p
            JOIN role_permission rp ON rp.permission_id = p.id
            JOIN user_role ur ON ur.role_id = rp.role_id
            WHERE ur.user_id = #{userId} AND p.deleted = FALSE
            ORDER BY p.code
            """)
    List<String> findPermissionCodes(Long userId);
}

