package com.pension.permission.domain.permission.enumeration;

/**
 * 权限点元数据来源。
 * API: 由 @RequirePermission 注解自动扫描注册；
 * MANUAL: 由管理后台人工新增（如某些不直接暴露 API 的能力点）。
 */
public enum PermissionItemSource {
  API, MANUAL
}
