package com.pension.permission.sdk;

/**
 * Permission服务不可达/返回异常状态码时抛出。调用方按fail-closed原则处理(见PermissionGuard)。
 */
public class PermissionServiceUnavailableException extends RuntimeException {
  public PermissionServiceUnavailableException(String message) {
    super(message);
  }

  public PermissionServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
