package com.pension.permission.domain.permission.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 权限元数据相关错误码。码段 08xx，与 auth-service 已有子模块（01xx-07xx）不冲突。
 */
@Getter
@AllArgsConstructor
public enum PermissionItemError implements ErrorDefinition {

  PERMISSION_ITEM_NOT_FOUND("SERVICE.AUTH.0801", "权限点不存在"),
  DUPLICATE_PERMISSION_ITEM("SERVICE.AUTH.0802", "权限点已存在"),
  INVALID_PERMISSION_CATEGORY("SERVICE.AUTH.0803", "权限类别无效"),

  ;

  private final String code;
  private final String message;
}
