package com.pension.permission.infrastructure.permission;

/**
 * 权限点扫描结果.
 *
 * @param totalReceived 发现的权限点数量
 * @param upserted      新增/更新数量
 * @param unchanged     未变化数量
 * @author auth-infrastructure
 */
public record ScanResult(int totalReceived, int upserted, int unchanged) {}
