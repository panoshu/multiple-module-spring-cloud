package com.example.shared.permission;

/**
 * 权限校验结果.
 *
 * @param allowed 是否允许
 * @param reason  拒绝原因（allowed=false 时可填，用于日志）
 * @author shared-permission-starter
 */
public record PermissionCheckResult(boolean allowed, String reason) {

  public static PermissionCheckResult allow() {
    return new PermissionCheckResult(true, null);
  }

  public static PermissionCheckResult deny(String reason) {
    return new PermissionCheckResult(false, reason);
  }
}
