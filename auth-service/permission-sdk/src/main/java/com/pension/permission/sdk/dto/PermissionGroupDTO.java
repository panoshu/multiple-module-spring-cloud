package com.pension.permission.sdk.dto;

import java.util.List;

/**
 * 权限分组 DTO（按 categoryGroup 聚合）。
 */
public record PermissionGroupDTO(
  String groupName,
  List<PermissionItemDTO> items
) {}
