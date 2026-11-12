package com.noticehub.controller;

import com.noticehub.common.ApiResponse;
import com.noticehub.dto.Requests;
import com.noticehub.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {

  private final UserService service;

  @GetMapping("/users")
  public ApiResponse<?> list(
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "10") int size
  ) {
    return ApiResponse.ok(service.list(q, page, size));
  }

  @PostMapping("/users")
  public ApiResponse<?> create(@Valid @RequestBody Requests.CreateUser body) {
    return ApiResponse.ok(service.create(body));
  }

  @PutMapping("/users/{id}")
  public ApiResponse<?> update(
    @PathVariable long id,
    @Valid @RequestBody Requests.UpdateUser body
  ) {
    service.update(id, body);
    return ApiResponse.ok(null);
  }

  @PutMapping("/users/{id}/enabled")
  public ApiResponse<?> enabled(@PathVariable long id, @Valid @RequestBody Requests.Enabled body) {
    service.enabled(id, body.enabled());
    return ApiResponse.ok(null);
  }

  @PutMapping("/users/{id}/roles")
  public ApiResponse<?> roles(@PathVariable long id, @Valid @RequestBody Requests.Roles body) {
    service.assign(id, body.roles());
    return ApiResponse.ok(null);
  }

  @GetMapping("/roles")
  public ApiResponse<?> allRoles() {
    return ApiResponse.ok(service.allRoles());
  }
}
