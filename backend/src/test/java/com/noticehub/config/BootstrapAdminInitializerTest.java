package com.noticehub.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.noticehub.domain.User;
import com.noticehub.mapper.AccessMapper;
import com.noticehub.mapper.UserMapper;
import com.noticehub.service.AuditService;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class BootstrapAdminInitializerTest {

  private UserMapper users;
  private AccessMapper access;
  private AuditService audit;
  private BootstrapAdminInitializer initializer;
  private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19456, 2);

  @BeforeEach
  void setup() {
    users = mock(UserMapper.class);
    access = mock(AccessMapper.class);
    audit = mock(AuditService.class);
    initializer = new BootstrapAdminInitializer(users, access, encoder, audit);
    ReflectionTestUtils.setField(initializer, "enabled", true);
    ReflectionTestUtils.setField(initializer, "username", "admin");
    ReflectionTestUtils.setField(initializer, "displayName", "Administrator");
    ReflectionTestUtils.setField(initializer, "password", "BootstrapPassword123!");
    when(users.selectCount(null)).thenReturn(0L);
    when(access.roleId("ADMIN")).thenReturn(3L);
  }

  @Test
  void existingDatabaseIsNeverReinitialized() {
    when(users.selectCount(null)).thenReturn(4L);
    ReflectionTestUtils.setField(initializer, "password", "");
    initializer.run(null);
    verify(users, never()).insert(any(User.class));
    verifyNoInteractions(access, audit);
  }

  @Test
  void freshDeploymentCreatesProtectedAdministratorWithRealPasswordHash() {
    AtomicReference<User> created = new AtomicReference<>();
    doAnswer(call -> {
      User u = call.getArgument(0);
      u.setId(42L);
      created.set(u);
      return 1;
    })
      .when(users)
      .insert(any(User.class));
    initializer.run(null);
    User admin = created.get();
    assertNotNull(admin);
    assertTrue(admin.getProtectedAccount());
    assertTrue(admin.getEnabled());
    assertTrue(admin.getPasswordHash().startsWith("$argon2id$"));
    assertTrue(encoder.matches("BootstrapPassword123!", admin.getPasswordHash()));
    verify(access).assign(42L, 3L);
  }

  @Test
  void invalidPasswordAndDemoSeedCannotCreateProductionAccounts() {
    ReflectionTestUtils.setField(initializer, "password", "short");
    assertThrows(IllegalStateException.class, () -> initializer.run(null));
    verify(users, never()).insert(any(User.class));
    ReflectionTestUtils.setField(initializer, "password", "BootstrapPassword123!");
    ReflectionTestUtils.setField(initializer, "demoEnabled", true);
    assertThrows(IllegalStateException.class, () -> initializer.run(null));
    verify(users, never()).insert(any(User.class));
  }
}
