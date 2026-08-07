package com.example.auth.api.dto;

import java.util.List;

/**
 * 批量权限校验响应.
 *
 * @param items 各权限点校验结果
 */
public record PermissionCheckBatchResponse(List<PermissionCheckItemResponse> items) {}
