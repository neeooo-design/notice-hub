package com.noticehub.domain;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("app_user")
public class User {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String username;

  @JsonIgnore
  private String passwordHash;

  private String displayName;
  private Boolean enabled;
  private Boolean protectedAccount;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
