package com.noticehub.dto;

import jakarta.validation.constraints.*;
import java.util.List;

public final class Requests {

  private Requests() {}

  public record Login(
    @NotBlank(message = "请输入用户名") @Size(max = 32) String username,
    @NotBlank(message = "请输入密码") @Size(max = 128) String password
  ) {}

  public record Profile(
    @NotBlank(message = "请输入姓名") @Size(max = 40, message = "姓名最多40个字") String displayName
  ) {}

  public record ChangePassword(
    @NotBlank @Size(max = 128) String oldPassword,
    @NotNull
    @Pattern(
      regexp = "(?s)(?=.*[A-Za-z])(?=.*[0-9]).{10,128}",
      message = "新密码需为10至128位，包含字母和数字"
    )
    String newPassword
  ) {}

  public record CreateUser(
    @NotBlank
    @Pattern(
      regexp = "[a-zA-Z][a-zA-Z0-9_]{2,31}",
      message = "用户名以字母开头，由3至32位字母、数字或下划线组成"
    )
    String username,
    @NotBlank @Size(max = 40) String displayName,
    @NotNull
    @Pattern(
      regexp = "(?s)(?=.*[A-Za-z])(?=.*[0-9]).{10,128}",
      message = "密码需为10至128位，包含字母和数字"
    )
    String password,
    @NotEmpty @Size(max = 3) List<@NotBlank String> roles
  ) {}

  public record UpdateUser(@NotBlank @Size(max = 40) String displayName) {}

  public record Enabled(@NotNull Boolean enabled) {}

  public record Roles(@NotEmpty @Size(max = 3) List<@NotBlank String> roles) {}

  public record NoticeInput(
    @NotBlank(message = "请输入通知标题")
    @Size(max = 120, message = "标题最多120个字")
    String title,
    @NotBlank(message = "请输入通知内容")
    @Size(max = 20000, message = "正文最多20000个字")
    String content,
    @NotNull
    @Pattern(regexp = "公司动态|行政通知|技术分享|活动资讯", message = "请选择有效分类")
    String category,
    @NotNull Boolean pinned,
    @Min(0) Integer version
  ) {}
}
