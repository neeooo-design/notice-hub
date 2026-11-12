package com.noticehub.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.noticehub.common.*;
import com.noticehub.domain.Notice;
import com.noticehub.dto.Requests;
import com.noticehub.mapper.NoticeMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoticeService {

  private final NoticeMapper mapper;
  private final AccessService access;
  private final AuditService audit;

  public PageResult<Notice> list(String q, String category, boolean mine, int page, int size) {
    access.check("notice:read");
    page = PageResult.page(page);
    size = PageResult.size(size);
    long owner = mine ? StpUtil.getLoginIdAsLong() : 0;
    return new PageResult<>(
      mapper.search(q, category, owner, size, (page - 1) * size),
      mapper.searchCount(q, category, owner),
      page,
      size
    );
  }

  public Notice detail(long id) {
    access.check("notice:read");
    return require(id);
  }

  @Transactional
  public long create(Requests.NoticeInput input) {
    access.check("notice:create");
    Notice n = new Notice();
    apply(n, input);
    n.setCreatedBy(StpUtil.getLoginIdAsLong());
    n.setVersion(0);
    n.setDeleted(false);
    n.setCreatedAt(LocalDateTime.now());
    n.setUpdatedAt(LocalDateTime.now());
    mapper.insert(n);
    audit.success("NOTICE_CREATE", "通知 #" + n.getId(), "发布：" + n.getTitle());
    return n.getId();
  }

  @Transactional
  public void update(long id, Requests.NoticeInput input) {
    Notice n = require(id);
    checkOwner(n, "notice:update:own");
    if (input.version() == null) throw new BusinessException(400, "缺少通知版本，请刷新后重试");
    apply(n, input);
    if (mapper.updateVersion(n, input.version()) != 1) throw new BusinessException(
      409,
      "这条通知已被其他人更新，请刷新后再编辑"
    );
    audit.success("NOTICE_UPDATE", "通知 #" + id, "修改：" + n.getTitle());
  }

  @Transactional
  public void delete(long id) {
    Notice n = require(id);
    checkOwner(n, "notice:delete:own");
    mapper.deleteById(id);
    audit.success("NOTICE_DELETE", "通知 #" + id, "删除：" + n.getTitle());
  }

  private Notice require(long id) {
    Notice n = mapper.detail(id);
    if (n == null) throw new BusinessException(404, "通知不存在或已删除");
    return n;
  }

  private void checkOwner(Notice n, String permission) {
    if (access.has("notice:manage")) return;
    access.check(permission);
    if (n.getCreatedBy() != StpUtil.getLoginIdAsLong()) throw new BusinessException(
      403,
      "只能操作自己创建的通知"
    );
  }

  private void apply(Notice n, Requests.NoticeInput input) {
    n.setTitle(input.title().trim());
    n.setContent(input.content().trim());
    n.setCategory(input.category());
    // 全局置顶属于管理能力，普通编辑不能通过伪造请求设置。
    if (
      input.pinned() != Boolean.TRUE.equals(n.getPinned()) && !access.has("notice:manage")
    ) throw new BusinessException(403, "只有管理员可以调整通知置顶状态");
    n.setPinned(input.pinned());
  }
}
