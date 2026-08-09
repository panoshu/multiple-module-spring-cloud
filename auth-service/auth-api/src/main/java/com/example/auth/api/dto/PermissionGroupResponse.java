package com.example.auth.api.dto;

import java.util.List;

/**
 * 权限项分组.
 *
 * @param groupName 分组名称
 * @param items     分组下权限项
 */
public record PermissionGroupResponse(String groupName, List<PermissionItemResponse> items) {
}
