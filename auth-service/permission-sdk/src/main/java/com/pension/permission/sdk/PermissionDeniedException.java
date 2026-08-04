package com.pension.permission.sdk;

public class PermissionDeniedException extends RuntimeException {
  public PermissionDeniedException(String accountId, String planId, String businessCode, String actionCode) {
    super("权限不足: account=" + accountId + ", plan=" + planId
      + ", business=" + businessCode + ", action=" + actionCode);
  }
}
