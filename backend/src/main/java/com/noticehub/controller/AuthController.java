package com.noticehub.controller;

import com.noticehub.common.ApiResponse;
import com.noticehub.dto.Requests;
import com.noticehub.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService auth;

  @PostMapping("/login")
  public ApiResponse<?> login(@Valid @RequestBody Requests.Login body) {
    return ApiResponse.ok(auth.login(body));
  }

  @GetMapping("/me")
  public ApiResponse<?> me() {
    return ApiResponse.ok(auth.me());
  }

  @PostMapping("/logout")
  public ApiResponse<?> logout() {
    auth.logout();
    return ApiResponse.ok(null);
  }

  @PutMapping("/profile")
  public ApiResponse<?> profile(@Valid @RequestBody Requests.Profile body) {
    auth.updateProfile(body);
    return ApiResponse.ok(auth.me());
  }

  @PutMapping("/password")
  public ApiResponse<?> password(@Valid @RequestBody Requests.ChangePassword body) {
    auth.changePassword(body);
    return ApiResponse.ok(null);
  }
}
