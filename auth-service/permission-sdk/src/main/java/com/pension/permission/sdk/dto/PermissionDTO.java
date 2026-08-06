package com.pension.permission.sdk.dto;

/**
 * 权限 DTO（用于缓存查询返回）。
 */
public record PermissionDTO(
  String businessCode,
  String actionCode
) {}
