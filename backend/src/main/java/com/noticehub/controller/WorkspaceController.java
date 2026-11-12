package com.noticehub.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.noticehub.common.*;
import com.noticehub.config.Readiness;
import com.noticehub.domain.*;
import com.noticehub.mapper.*;
import com.noticehub.service.AccessService;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WorkspaceController {

  private final AccessService access;
  private final NoticeMapper notices;
  private final UserMapper users;
  private final AuditMapper audits;
  private final JdbcTemplate jdbc;
  private final StringRedisTemplate redis;
  private final Readiness readiness;

  @GetMapping("/health")
  public ApiResponse<?> health() {
    if (!readiness.isReady()) throw new BusinessException(503, "服务正在初始化");
    try {
      jdbc.queryForObject("SELECT 1", Integer.class);
      try (var connection = redis.getConnectionFactory().getConnection()) {
        connection.ping();
      }
    } catch (Exception e) {
      throw new BusinessException(503, "依赖服务尚未就绪");
    }
    return ApiResponse.ok(Map.of("status", "UP", "application", "notice-hub"));
  }

  @GetMapping("/dashboard")
  public ApiResponse<?> dashboard() {
    access.check("notice:read");
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("noticeCount", notices.selectCount(null));
    result.put(
      "myNoticeCount",
      notices.selectCount(new QueryWrapper<Notice>().eq("created_by", StpUtil.getLoginIdAsLong()))
    );
    result.put("recent", notices.search("", "", 0, 4, 0));
    if (access.has("user:read")) {
      result.put("userCount", users.selectCount(null));
      result.put(
        "activeUserCount",
        users.selectCount(new QueryWrapper<User>().eq("enabled", true))
      );
    }
    if (access.has("audit:read")) result.put("recentActivity", audits.search("", "", 5, 0));
    return ApiResponse.ok(result);
  }

  @GetMapping("/audit-logs")
  public ApiResponse<?> audit(
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "") String action,
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "12") int size
  ) {
    access.check("audit:read");
    page = PageResult.page(page);
    size = PageResult.size(size);
    return ApiResponse.ok(
      new PageResult<>(
        audits.search(q, action, size, (page - 1) * size),
        audits.searchCount(q, action),
        page,
        size
      )
    );
  }
}
