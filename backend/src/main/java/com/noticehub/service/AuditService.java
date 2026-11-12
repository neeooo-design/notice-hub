package com.noticehub.service;

import cn.dev33.satoken.stp.StpUtil;
import com.noticehub.domain.AuditLog;
import com.noticehub.domain.User;
import com.noticehub.mapper.AuditMapper;
import com.noticehub.mapper.UserMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {

  private final AuditMapper mapper;
  private final UserMapper users;

  public void write(
    Long actorId,
    String actorName,
    String action,
    String target,
    String outcome,
    String detail
  ) {
    AuditLog item = new AuditLog();
    item.setActorId(actorId);
    item.setActorName(actorName);
    item.setAction(action);
    item.setTarget(target);
    item.setOutcome(outcome);
    item.setDetail(detail);
    item.setCreatedAt(LocalDateTime.now());
    mapper.insert(item);
  }

  public void success(String action, String target, String detail) {
    long id = StpUtil.getLoginIdAsLong();
    User u = users.selectById(id);
    write(id, u.getDisplayName(), action, target, "SUCCESS", detail);
  }
}
