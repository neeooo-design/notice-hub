package com.noticehub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.noticehub.domain.User;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface UserMapper extends BaseMapper<User> {
  @Select("SELECT * FROM app_user WHERE username=#{username}")
  User byUsername(String username);

  @Select(
    "SELECT * FROM app_user WHERE username LIKE CONCAT('%',#{q},'%') OR display_name LIKE CONCAT('%',#{q},'%') ORDER BY id LIMIT #{limit} OFFSET #{offset}"
  )
  List<User> search(@Param("q") String q, @Param("limit") int limit, @Param("offset") int offset);

  @Select(
    "SELECT COUNT(*) FROM app_user WHERE username LIKE CONCAT('%',#{q},'%') OR display_name LIKE CONCAT('%',#{q},'%')"
  )
  long searchCount(String q);
}
