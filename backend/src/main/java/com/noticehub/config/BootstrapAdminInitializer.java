package com.noticehub.config;

import com.noticehub.domain.User;
import com.noticehub.mapper.AccessMapper;
import com.noticehub.mapper.UserMapper;
import com.noticehub.service.AuditService;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates exactly one protected administrator for a fresh deployment. */
@Component
@Order(100)
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

  private final UserMapper users;
  private final AccessMapper access;
  private final PasswordEncoder encoder;
  private final AuditService audit;

  @Value("${app.bootstrap-admin.enabled:false}")
  private boolean enabled;

  @Value("${app.seed-demo:false}")
  private boolean demoEnabled;

  @Value("${app.bootstrap-admin.username:admin}")
  private String username;

  @Value("${app.bootstrap-admin.display-name:Administrator}")
  private String displayName;

  @Value("${app.bootstrap-admin.password:}")
  private String password;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!enabled) return;
    if (demoEnabled) throw new IllegalStateException(
      "Bootstrap admin and demo seed cannot be enabled together"
    );
    // Existing accounts, roles and passwords must never be overwritten at startup.
    if (users.selectCount(null) > 0) return;
    if (
      username == null || !username.matches("[A-Za-z][A-Za-z0-9_]{2,31}")
    ) throw new IllegalStateException(
      "Bootstrap username must use 3-32 ASCII letters, digits or underscores"
    );
    if (
      displayName == null || displayName.isBlank() || displayName.length() > 40
    ) throw new IllegalStateException("Bootstrap display name must use 1-40 characters");
    if (
      password == null || !password.matches("(?s)(?=.*[A-Za-z])(?=.*[0-9]).{16,128}")
    ) throw new IllegalStateException(
      "Bootstrap password must use 16-128 characters with letters and digits"
    );
    Long adminRole = access.roleId("ADMIN");
    if (adminRole == null) throw new IllegalStateException(
      "ADMIN role is missing; database migration must complete first"
    );
    User admin = new User();
    admin.setUsername(username.toLowerCase(Locale.ROOT));
    admin.setDisplayName(displayName.trim());
    admin.setPasswordHash(encoder.encode(password));
    admin.setEnabled(true);
    admin.setProtectedAccount(true);
    admin.setCreatedAt(LocalDateTime.now());
    admin.setUpdatedAt(admin.getCreatedAt());
    users.insert(admin);
    access.assign(admin.getId(), adminRole);
    audit.write(
      admin.getId(),
      admin.getDisplayName(),
      "ADMIN_BOOTSTRAP",
      "Initial administrator",
      "SUCCESS",
      "Created protected administrator for an empty deployment"
    );
  }
}
