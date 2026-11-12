package com.noticehub.service;

import cn.dev33.satoken.stp.StpUtil;
import com.noticehub.common.*;
import com.noticehub.domain.User;
import com.noticehub.dto.Requests;
import com.noticehub.mapper.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserMapper users;
  private final AccessMapper roles;
  private final AccessService access;
  private final AuthService auth;
  private final AuditService audit;
  private final PasswordEncoder encoder;

  public PageResult<Map<String, Object>> list(String q, int page, int size) {
    access.check("user:read");
    page = PageResult.page(page);
    size = PageResult.size(size);
    return new PageResult<>(
      users
        .search(q, size, (page - 1) * size)
        .stream()
        .map(auth::profile)
        .toList(),
      users.searchCount(q),
      page,
      size
    );
  }

  @Transactional
  public long create(Requests.CreateUser request) {
    access.check("user:create");
    access.check("role:assign");
    List<Long> ids = validateRoles(request.roles());
    User u = new User();
    u.setUsername(request.username().toLowerCase(Locale.ROOT));
    u.setDisplayName(request.displayName().trim());
    u.setPasswordHash(encoder.encode(request.password()));
    u.setEnabled(true);
    u.setProtectedAccount(false);
    u.setCreatedAt(LocalDateTime.now());
    u.setUpdatedAt(LocalDateTime.now());
    users.insert(u);
    ids.forEach(id -> roles.assign(u.getId(), id));
    audit.success(
      "USER_CREATE",
      u.getUsername(),
      "创建账号；角色：" + String.join(",", request.roles())
    );
    return u.getId();
  }

  @Transactional
  public void update(long id, Requests.UpdateUser request) {
    access.check("user:update");
    User u = require(id);
    u.setDisplayName(request.displayName().trim());
    users.updateById(u);
    audit.success("USER_UPDATE", u.getUsername(), "更新显示姓名");
  }

  @Transactional
  public void enabled(long id, boolean enabled) {
    access.check("user:update");
    User u = require(id);
    protect(u);
    u.setEnabled(enabled);
    users.updateById(u);
    if (!enabled) StpUtil.logout(id);
    audit.success(
      enabled ? "USER_ENABLE" : "USER_DISABLE",
      u.getUsername(),
      enabled ? "启用账号" : "停用账号并注销全部登录"
    );
  }

  @Transactional
  public void assign(long id, List<String> codes) {
    access.check("role:assign");
    User u = require(id);
    protect(u);
    List<Long> ids = validateRoles(codes);
    roles.clearRoles(id);
    ids.forEach(roleId -> roles.assign(id, roleId));
    audit.success("ROLE_ASSIGN", u.getUsername(), "角色调整为：" + String.join(",", codes));
  }

  public List<Map<String, Object>> allRoles() {
    access.check("role:read");
    return roles
      .allRoles()
      .stream()
      .map(row -> {
        Map<String, Object> item = new LinkedHashMap<>(row);
        item.put("permissions", roles.rolePermissions(((Number) row.get("id")).longValue()));
        return item;
      })
      .toList();
  }

  private User require(long id) {
    User u = users.selectById(id);
    if (u == null) throw new BusinessException(404, "用户不存在");
    return u;
  }

  private void protect(User u) {
    if (Boolean.TRUE.equals(u.getProtectedAccount())) throw new BusinessException(
      403,
      "初始管理员为保护账号，不能停用或调整角色"
    );
    if (u.getId() == StpUtil.getLoginIdAsLong()) throw new BusinessException(
      403,
      "请使用其他管理员账号调整自己的状态或角色"
    );
  }

  private List<Long> validateRoles(List<String> codes) {
    return codes
      .stream()
      .distinct()
      .map(code -> {
        Long id = roles.roleId(code);
        if (id == null) throw new BusinessException(400, "角色不存在：" + code);
        return id;
      })
      .toList();
  }
}
