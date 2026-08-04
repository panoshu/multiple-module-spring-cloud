package com.pension.permission.sdk;

/**
 * 批量检查时的单项，actionCode可为null(代表不区分操作)
 */
public record PermissionCheckRequest(String businessCode, String actionCode) {
}
