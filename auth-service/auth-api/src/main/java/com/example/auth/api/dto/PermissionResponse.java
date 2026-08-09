package com.example.auth.api.dto;

/**
 * 权限项（业务编码 + 操作编码）.
 *
 * @param businessCode 业务编码
 * @param actionCode   操作编码
 */
public record PermissionResponse(String businessCode, String actionCode) {
}
