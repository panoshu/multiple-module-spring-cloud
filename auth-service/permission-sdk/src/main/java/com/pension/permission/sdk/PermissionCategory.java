package com.pension.permission.sdk;

/**
 * 权限类别镜像枚举（permission-sdk 内部独立定义，避免 SDK 依赖 auth-domain）。
 * 与 auth-domain 的 PermissionCategory 一一对应，值必须保持一致。
 */
public enum PermissionCategory {
  BUSINESS,
  PLATFORM
}
