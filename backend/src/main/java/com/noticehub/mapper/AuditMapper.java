package com.noticehub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.noticehub.domain.AuditLog;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface AuditMapper extends BaseMapper<AuditLog> {
  String FILTER =
    " FROM audit_log WHERE (#{action}='' OR action=#{action}) AND (actor_name LIKE CONCAT('%',#{q},'%') OR target LIKE CONCAT('%',#{q},'%')) ";

  @Select("SELECT *" + FILTER + "ORDER BY id DESC LIMIT #{limit} OFFSET #{offset}")
  List<AuditLog> search(
    @Param("q") String q,
    @Param("action") String action,
    @Param("limit") int limit,
    @Param("offset") int offset
  );

  @Select("SELECT COUNT(*)" + FILTER)
  long searchCount(@Param("q") String q, @Param("action") String action);
}
