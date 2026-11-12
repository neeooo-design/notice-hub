package com.noticehub.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.noticehub.domain.*;
import com.noticehub.mapper.*;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DemoInitializer implements ApplicationRunner {

  private final UserMapper users;
  private final AccessMapper access;
  private final NoticeMapper notices;
  private final PasswordEncoder encoder;

  @Value("${app.seed-demo:false}")
  private boolean enabled;

  @Value("${app.seed-password:}")
  private String password;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!enabled || users.selectCount(null) > 0) return;
    if (password.length() < 10) throw new IllegalStateException(
      "Set app.seed-password before enabling the local demo seed"
    );
    User admin = user("admin", "林舟", 3L, true);
    User editor = user("editor", "陈可", 2L, false);
    user("employee", "周宁", 1L, false);
    User other = user("editor2", "许然", 2L, false);
    notice(
      admin,
      "欢迎使用 Notice Hub",
      "这里是团队的内部信息空间。\n\n你可以在通知中心阅读公司动态、行政通知和技术分享。管理员负责维护账号与角色，编辑可以发布并维护自己的内容。\n\n从一条清晰的通知开始，让团队的信息流动起来。",
      "公司动态",
      true,
      3
    );
    notice(
      editor,
      "本周技术分享：从一次请求理解后端",
      "时间：本周五 15:00—16:00\n地点：三楼讨论区\n\n分享将围绕一次实际请求，介绍参数校验、业务处理和数据查询。欢迎带着问题参与。",
      "技术分享",
      false,
      2
    );
    notice(
      admin,
      "办公区域与会议室使用提醒",
      "请在使用会议室前完成预约，结束后及时整理桌面、关闭投影。\n\n如有设备问题，请联系行政同事。感谢大家共同维护舒适的办公环境。",
      "行政通知",
      false,
      2
    );
    notice(
      other,
      "一起出发，周末城市漫步",
      "本周末组织一次轻松的城市漫步。\n\n集合时间：周六上午 09:30\n集合地点：公司一楼大厅\n\n自愿参加，请准备舒适的鞋和饮用水。",
      "活动资讯",
      false,
      1
    );
    notice(
      editor,
      "接口协作约定：让沟通更顺畅",
      "接口交付时，请同步提供请求示例、字段说明和错误响应。\n\n发生字段调整时，在联调前说明变更内容。涉及权限的接口，补充不同角色下的验证结果。",
      "技术分享",
      false,
      1
    );
  }

  private User user(String username, String name, long role, boolean protect) {
    User u = new User();
    u.setUsername(username);
    u.setDisplayName(name);
    u.setPasswordHash(encoder.encode(password));
    u.setEnabled(true);
    u.setProtectedAccount(protect);
    u.setCreatedAt(LocalDateTime.now());
    u.setUpdatedAt(LocalDateTime.now());
    users.insert(u);
    access.assign(u.getId(), role);
    return u;
  }

  private void notice(
    User author,
    String title,
    String content,
    String category,
    boolean pinned,
    int daysAgo
  ) {
    Notice n = new Notice();
    n.setTitle(title);
    n.setContent(content);
    n.setCategory(category);
    n.setPinned(pinned);
    n.setCreatedBy(author.getId());
    n.setVersion(0);
    n.setDeleted(false);
    n.setCreatedAt(LocalDateTime.now().minusDays(daysAgo));
    n.setUpdatedAt(n.getCreatedAt());
    notices.insert(n);
  }
}
