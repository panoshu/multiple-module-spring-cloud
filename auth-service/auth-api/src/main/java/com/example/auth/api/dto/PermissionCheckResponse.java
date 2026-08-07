package com.example.auth.api.dto;

/**
 * 单点权限校验响应.
 *
 * @param allowed 是否允许
 */
public record PermissionCheckResponse(boolean allowed) {}
