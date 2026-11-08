package com.example.shared.permission;

/**
 * 权限校验上下文（切面 → Executor 传递的入参）.
 *
 * @param accountId    账号 ID
 * @param planId       计划 ID（PLATFORM 类权限为 null）
 * @param businessCode 业务编码
 * @param actionCode   操作编码（null 表示不区分操作）
 * @author shared-permission-starter
 */
public record PermissionCheckContext(
  String accountId,
  String planId,
  String businessCode,
  String actionCode) {
}
