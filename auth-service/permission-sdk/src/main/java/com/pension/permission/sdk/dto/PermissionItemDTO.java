package com.pension.permission.sdk.dto;

/**
 * 权限点 DTO（业务服务消费）。
 */
public record PermissionItemDTO(
  String businessCode,
  String actionCode,
  String category,
  String source,
  String controller,
  String method,
  String httpMethod,
  String path,
  String displayName,
  String description,
  String categoryGroup,
  int sortOrder
) {}
