package com.noticehub.service;

import cn.dev33.satoken.stp.StpUtil;
import com.noticehub.common.BusinessException;
import com.noticehub.domain.User;
import com.noticehub.dto.Requests;
import com.noticehub.mapper.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserMapper users;
  private final AccessMapper permissions;
  private final AccessService access;
  private final AuditService audit;
  private final PasswordEncoder encoder;
  private final StringRedisTemplate redis;
  private String dummyHash;
  private static final DefaultRedisScript<Long> FAIL_SCRIPT = new DefaultRedisScript<>(
    "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],600) end; return n",
    Long.class
  );

  @jakarta.annotation.PostConstruct
  void init() {
    dummyHash = encoder.encode(UUID.randomUUID().toString());
  }

  public Map<String, Object> login(Requests.Login request) {
    String username = request.username().trim().toLowerCase(Locale.ROOT);
    String key = rateKey(username);
    String attempts = redis.opsForValue().get(key);
    if (attempts != null && Integer.parseInt(attempts) >= 5) throw new BusinessException(
      429,
      "登录失败次数过多，请10分钟后重试"
    );
    User u = users.byUsername(username);
    boolean matches = encoder.matches(
      request.password(),
      u == null ? dummyHash : u.getPasswordHash()
    );
    if (u == null || !matches) {
      redis.execute(FAIL_SCRIPT, List.of(key));
      audit.write(null, username, "LOGIN", "账号登录", "FAILED", "用户名或密码错误");
      throw new BusinessException(401, "用户名或密码错误");
    }
    if (!Boolean.TRUE.equals(u.getEnabled())) {
      audit.write(u.getId(), u.getDisplayName(), "LOGIN", "账号登录", "FAILED", "账号已停用");
      throw new BusinessException(403, "账号已停用，请联系管理员");
    }
    redis.delete(key);
    StpUtil.login(u.getId());
    audit.write(u.getId(), u.getDisplayName(), "LOGIN", "账号登录", "SUCCESS", "账号密码登录");
    return Map.of(
      "token",
      StpUtil.getTokenValue(),
      "tokenName",
      StpUtil.getTokenName(),
      "expiresIn",
      StpUtil.getTokenTimeout(),
      "user",
      profile(u)
    );
  }

  public Map<String, Object> me() {
    return profile(access.current());
  }

  public Map<String, Object> profile(User u) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("id", u.getId());
    result.put("username", u.getUsername());
    result.put("displayName", u.getDisplayName());
    result.put("enabled", u.getEnabled());
    result.put("protectedAccount", u.getProtectedAccount());
    result.put("createdAt", u.getCreatedAt());
    result.put("roles", permissions.roles(u.getId()));
    result.put("permissions", permissions.permissions(u.getId()));
    return result;
  }

  public void logout() {
    audit.success("LOGOUT", "账号退出", "当前登录凭证失效");
    StpUtil.logout();
  }

  @Transactional
  public void updateProfile(Requests.Profile request) {
    User u = access.current();
    u.setDisplayName(request.displayName().trim());
    users.updateById(u);
    audit.success("PROFILE_UPDATE", "个人资料", "更新显示姓名");
  }

  @Transactional
  public void changePassword(Requests.ChangePassword request) {
    User u = access.current();
    if (!encoder.matches(request.oldPassword(), u.getPasswordHash())) throw new BusinessException(
      400,
      "当前密码不正确"
    );
    if (encoder.matches(request.newPassword(), u.getPasswordHash())) throw new BusinessException(
      400,
      "新密码不能与当前密码相同"
    );
    u.setPasswordHash(encoder.encode(request.newPassword()));
    users.updateById(u);
    audit.success("PASSWORD_CHANGE", "个人密码", "修改密码并注销全部登录");
    StpUtil.logout(u.getId());
  }

  private String rateKey(String username) {
    try {
      return (
        "noticehub:login-fail:" +
        HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(username.getBytes(StandardCharsets.UTF_8))
        )
      );
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
