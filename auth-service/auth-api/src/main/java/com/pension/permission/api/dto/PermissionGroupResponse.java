package com.pension.permission.api.dto;

import java.util.List;

public record PermissionGroupResponse(
  String groupName,
  List<PermissionItemResponse> items
) {}
