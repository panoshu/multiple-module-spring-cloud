package com.example.auth.api.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 批量权限校验请求.
 *
 * @param accountId 账号 ID
 * @param planId    计划编号
 * @param items     权限点列表
 */
public record PermissionCheckBatchRequest(
    @NotBlank String accountId,
    String planId,
    @NotEmpty @Valid List<PermissionCheckItemRequest> items) {}
