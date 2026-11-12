package com.noticehub.controller;

import com.noticehub.common.ApiResponse;
import com.noticehub.dto.Requests;
import com.noticehub.service.NoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

  private final NoticeService service;

  @GetMapping
  public ApiResponse<?> list(
    @RequestParam(defaultValue = "") String q,
    @RequestParam(defaultValue = "") String category,
    @RequestParam(defaultValue = "false") boolean mine,
    @RequestParam(defaultValue = "1") int page,
    @RequestParam(defaultValue = "9") int size
  ) {
    return ApiResponse.ok(service.list(q, category, mine, page, size));
  }

  @GetMapping("/{id}")
  public ApiResponse<?> detail(@PathVariable long id) {
    return ApiResponse.ok(service.detail(id));
  }

  @PostMapping
  public ApiResponse<?> create(@Valid @RequestBody Requests.NoticeInput body) {
    return ApiResponse.ok(service.create(body));
  }

  @PutMapping("/{id}")
  public ApiResponse<?> update(
    @PathVariable long id,
    @Valid @RequestBody Requests.NoticeInput body
  ) {
    service.update(id, body);
    return ApiResponse.ok(null);
  }

  @DeleteMapping("/{id}")
  public ApiResponse<?> delete(@PathVariable long id) {
    service.delete(id);
    return ApiResponse.ok(null);
  }
}
