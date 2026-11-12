package com.noticehub.service;

import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.noticehub.common.BusinessException;
import com.noticehub.domain.User;
import com.noticehub.mapper.AccessMapper;
import com.noticehub.mapper.UserMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccessService implements StpInterface {

  private final AccessMapper access;
  private final UserMapper users;

  // 每次向数据库读取当前权限，角色变更在下一个请求生效。
  @Override
  public List<String> getPermissionList(Object loginId, String loginType) {
    return access.permissions(Long.parseLong(loginId.toString()));
  }

  @Override
  public List<String> getRoleList(Object loginId, String loginType) {
    return access.roles(Long.parseLong(loginId.toString()));
  }

  public User current() {
    User u = users.selectById(StpUtil.getLoginIdAsLong());
    if (u == null || !Boolean.TRUE.equals(u.getEnabled())) throw new BusinessException(
      401,
      "账号已停用，请联系管理员"
    );
    return u;
  }

  public void check(String permission) {
    StpUtil.checkPermission(permission);
  }

  public boolean has(String permission) {
    return StpUtil.hasPermission(permission);
  }
}
