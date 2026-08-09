package com.example.auth.api.dto;

/**
 * 批量校验单项结果.
 *
 * @param businessCode 业务编码
 * @param actionCode   操作编码
 * @param allowed      是否允许
 */
public record PermissionCheckItemResponse(String businessCode, String actionCode, boolean allowed) {
}
