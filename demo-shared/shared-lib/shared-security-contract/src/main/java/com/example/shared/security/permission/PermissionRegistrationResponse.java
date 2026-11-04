package com.example.shared.security.permission;

/**
 * 权限点上报响应.
 *
 * @param totalReceived 接收的权限点数量
 * @param upserted      新增或更新的数量
 * @param unchanged     未变化的数量
 * @author auth-api
 */
public record PermissionRegistrationResponse(
  int totalReceived,
  int upserted,
  int unchanged) {
}
