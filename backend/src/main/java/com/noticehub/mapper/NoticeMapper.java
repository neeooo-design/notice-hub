package com.noticehub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.noticehub.domain.Notice;
import java.util.List;
import org.apache.ibatis.annotations.*;

public interface NoticeMapper extends BaseMapper<Notice> {
  String FILTER =
    " FROM notice n JOIN app_user u ON n.created_by=u.id WHERE n.deleted=0 AND (n.title LIKE CONCAT('%',#{q},'%') OR n.content LIKE CONCAT('%',#{q},'%')) AND (#{category}='' OR n.category=#{category}) AND (#{owner}=0 OR n.created_by=#{owner}) ";

  @Select(
    "SELECT n.*,u.display_name AS author_name" +
      FILTER +
      "ORDER BY n.pinned DESC,n.created_at DESC,n.id DESC LIMIT #{limit} OFFSET #{offset}"
  )
  List<Notice> search(
    @Param("q") String q,
    @Param("category") String category,
    @Param("owner") long owner,
    @Param("limit") int limit,
    @Param("offset") int offset
  );

  @Select("SELECT COUNT(*)" + FILTER)
  long searchCount(
    @Param("q") String q,
    @Param("category") String category,
    @Param("owner") long owner
  );

  @Select(
    "SELECT n.*,u.display_name AS author_name FROM notice n JOIN app_user u ON u.id=n.created_by WHERE n.id=#{id} AND n.deleted=0"
  )
  Notice detail(long id);

  @Update(
    "UPDATE notice SET title=#{n.title},content=#{n.content},category=#{n.category},pinned=#{n.pinned},version=version+1,updated_at=NOW() WHERE id=#{n.id} AND version=#{expected} AND deleted=0"
  )
  int updateVersion(@Param("n") Notice n, @Param("expected") int expected);
}
