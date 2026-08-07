package com.example.auth.api.annotation;

/**
 * 权限类别.
 *
 * <p>BUSINESS：业务权限，需要 planId 维度校验；PLATFORM：平台权限，不区分计划.
 *
 * @author auth-api
 */
public enum PermissionCategory {
    BUSINESS,
    PLATFORM
}
