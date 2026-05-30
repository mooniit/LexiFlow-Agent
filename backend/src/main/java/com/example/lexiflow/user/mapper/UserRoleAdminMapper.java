package com.example.lexiflow.user.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserRoleAdminMapper {

    @Select("""
            SELECT r.code
            FROM role r
            JOIN user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.deleted = FALSE
            ORDER BY r.code
            """)
    List<String> findRoleCodes(Long userId);

    @Delete("DELETE FROM user_role WHERE user_id = #{userId}")
    void deleteUserRoles(Long userId);

    @Insert("""
            INSERT INTO user_role (user_id, role_id)
            SELECT #{userId}, r.id
            FROM role r
            WHERE r.code = #{roleCode} AND r.deleted = FALSE
            ON CONFLICT DO NOTHING
            """)
    void insertUserRole(@Param("userId") Long userId, @Param("roleCode") String roleCode);
}
