package com.example.auth.api.query;

import jakarta.validation.constraints.NotBlank;

/**
 * 批量校验单项请求.
 *
 * @param businessCode 业务编码
 * @param actionCode   操作编码
 */
public record PermissionCheckItemRequest(@NotBlank String businessCode, String actionCode) {}
