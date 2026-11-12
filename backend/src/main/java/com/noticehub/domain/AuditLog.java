package com.noticehub.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("audit_log")
public class AuditLog {

  @TableId(type = IdType.AUTO)
  private Long id;

  private Long actorId;
  private String actorName;
  private String action;
  private String target;
  private String outcome;
  private String detail;
  private LocalDateTime createdAt;
}
