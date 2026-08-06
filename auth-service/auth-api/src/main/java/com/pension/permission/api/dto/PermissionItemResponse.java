package com.pension.permission.api.dto;

public record PermissionItemResponse(
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
