package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询平台权限请求.
 *
 * @param accountId 账号 ID
 */
public record GetPlatformPermissionsRequest(@NotBlank String accountId) {}
