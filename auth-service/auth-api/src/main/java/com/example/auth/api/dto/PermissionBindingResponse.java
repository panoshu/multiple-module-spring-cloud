package com.example.auth.api.dto;

/**
 * 权限绑定响应.
 *
 * @param bindingId    绑定ID
 * @param businessCode 业务编码
 * @param actionCode   操作编码（null 表示整个业务）
 */
public record PermissionBindingResponse(
  String bindingId,
  String businessCode,
  String actionCode
) {
}
