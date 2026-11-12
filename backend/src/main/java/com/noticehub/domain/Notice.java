package com.noticehub.domain;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("notice")
public class Notice {

  @TableId(type = IdType.AUTO)
  private Long id;

  private String title;
  private String content;
  private String category;
  private Boolean pinned;
  private Long createdBy;
  private Integer version;

  @TableLogic
  private Boolean deleted;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  @TableField(exist = false)
  private String authorName;
}
