package com.example.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 权限绑定项.
 *
 * @param businessCode 业务编码
 * @param actionCode   操作编码（空串或null 表示整个业务）
 */
public record BindingItem(
  @NotBlank(message = "businessCode不能为空") String businessCode,
  String actionCode
) {
}
