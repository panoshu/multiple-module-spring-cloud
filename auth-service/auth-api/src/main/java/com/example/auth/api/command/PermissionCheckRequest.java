package com.example.auth.api.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 单点权限校验请求.
 *
 * @param accountId    账号 ID
 * @param planId       计划编号（平台类权限可传 null）
 * @param businessCode 业务编码
 * @param actionCode   操作编码（null 表示不区分操作）
 */
public record PermissionCheckRequest(
    @NotBlank String accountId,
    String planId,
    @NotBlank String businessCode,
    String actionCode) {}
