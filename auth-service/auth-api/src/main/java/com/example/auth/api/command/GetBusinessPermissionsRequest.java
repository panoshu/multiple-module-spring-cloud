package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 查询业务权限请求.
 *
 * @param accountId 账号 ID
 * @param planId    计划编号
 */
public record GetBusinessPermissionsRequest(@NotBlank String accountId, @NotBlank String planId) {}
