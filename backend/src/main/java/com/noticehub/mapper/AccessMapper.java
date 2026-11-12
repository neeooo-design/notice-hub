package com.noticehub.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

public interface AccessMapper {
  @Select(
    "SELECT r.code FROM app_role r JOIN user_role ur ON ur.role_id=r.id WHERE ur.user_id=#{userId} ORDER BY r.id"
  )
  List<String> roles(long userId);

  @Select(
    "SELECT DISTINCT p.code FROM app_permission p JOIN role_permission rp ON rp.permission_id=p.id JOIN user_role ur ON ur.role_id=rp.role_id WHERE ur.user_id=#{userId} ORDER BY p.code"
  )
  List<String> permissions(long userId);

  @Select(
    "SELECT id,code,name,description,(SELECT COUNT(*) FROM user_role ur WHERE ur.role_id=r.id) AS memberCount FROM app_role r ORDER BY id"
  )
  List<Map<String, Object>> allRoles();

  @Select(
    "SELECT p.code,p.name,p.module FROM app_permission p JOIN role_permission rp ON rp.permission_id=p.id WHERE rp.role_id=#{roleId} ORDER BY p.id"
  )
  List<Map<String, Object>> rolePermissions(long roleId);

  @Select("SELECT id FROM app_role WHERE code=#{code}")
  Long roleId(String code);

  @Insert("INSERT INTO user_role(user_id,role_id) VALUES(#{userId},#{roleId})")
  void assign(@Param("userId") long userId, @Param("roleId") long roleId);

  @Delete("DELETE FROM user_role WHERE user_id=#{userId}")
  void clearRoles(long userId);
}
