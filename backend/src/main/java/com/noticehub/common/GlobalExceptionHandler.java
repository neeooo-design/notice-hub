package com.noticehub.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<?> business(BusinessException e) {
    return fail(e.getStatus(), e.getMessage());
  }

  @ExceptionHandler(NotLoginException.class)
  ResponseEntity<?> login(NotLoginException e) {
    return fail(401, "登录已失效，请重新登录");
  }

  @ExceptionHandler({ NotPermissionException.class, NotRoleException.class })
  ResponseEntity<?> permission(Exception e) {
    return fail(403, "你没有执行此操作的权限");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    return fail(
      400,
      e
        .getBindingResult()
        .getFieldErrors()
        .stream()
        .findFirst()
        .map(x -> x.getDefaultMessage())
        .orElse("请检查输入")
    );
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    ConstraintViolationException.class,
    MethodArgumentTypeMismatchException.class,
  })
  ResponseEntity<?> invalid(Exception e) {
    return fail(400, "请求参数格式不正确或包含不允许的字段");
  }

  @ExceptionHandler(DuplicateKeyException.class)
  ResponseEntity<?> duplicate(DuplicateKeyException e) {
    return fail(409, "用户名已存在");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<?> missing(NoResourceFoundException e) {
    return fail(404, "接口或资源不存在");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<?> method(HttpRequestMethodNotSupportedException e) {
    return fail(405, "此接口不支持当前请求方法");
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<?> parameter(MissingServletRequestParameterException e) {
    return fail(400, "缺少必需的请求参数");
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    log.error("Request failed", e);
    return fail(500, "服务暂时不可用，请稍后重试");
  }

  private ResponseEntity<?> fail(int status, String message) {
    return ResponseEntity.status(status).body(ApiResponse.error(status, message));
  }
}
