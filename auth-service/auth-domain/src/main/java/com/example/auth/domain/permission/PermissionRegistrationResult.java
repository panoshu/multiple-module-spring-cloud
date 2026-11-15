package com.example.auth.domain.permission;

/**
 * 外部业务服务权限点上报结果（AU-04：domain 端口返回值，adapter 不依赖 infrastructure）。
 *
 * @param totalReceived 接收的权限点数量
 * @param upserted      新增/更新数量
 * @param unchanged     未变化数量
 */
public record PermissionRegistrationResult(int totalReceived, int upserted, int unchanged) {
}
