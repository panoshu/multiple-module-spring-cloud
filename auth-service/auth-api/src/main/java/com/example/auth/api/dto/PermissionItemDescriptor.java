package com.example.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 权限点描述符（业务服务上报用）.
 *
 * @param businessCode 业务编码
 * @param actionCode   操作编码（null 表示不区分操作）
 * @param category     权限类别（BUSINESS / PLATFORM）
 * @param controller   Controller 类名
 * @param method       方法名
 * @param httpMethod   HTTP 方法（GET/POST 等）
 * @param path         请求路径
 * @author auth-api
 */
public record PermissionItemDescriptor(
  @NotBlank String businessCode,
  String actionCode,
  @NotBlank String category,
  String controller,
  String method,
  String httpMethod,
  String path) {
}
