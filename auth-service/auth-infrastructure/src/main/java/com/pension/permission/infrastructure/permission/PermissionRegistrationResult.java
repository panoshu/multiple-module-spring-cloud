package com.pension.permission.infrastructure.permission;

/**
 * 外部业务服务权限点上报结果.
 *
 * @param totalReceived 接收的权限点数量
 * @param upserted      新增/更新数量
 * @param unchanged     未变化数量
 * @author auth-infrastructure
 */
public record PermissionRegistrationResult(int totalReceived, int upserted, int unchanged) {
}
