package com.noticehub.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import com.noticehub.service.AccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

  private final AccessService access;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry
      .addInterceptor(
        new SaInterceptor(handler -> {
          StpUtil.checkLogin();
          access.current();
        })
      )
      .addPathPatterns("/api/**")
      .excludePathPatterns("/api/auth/login", "/api/health");
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    // Argon2id: 19 MiB memory, 2 iterations, parallelism 1.
    return new Argon2PasswordEncoder(16, 32, 1, 19456, 2);
  }
}
