package com.pension.permission.domain.permission.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 权限菜单相关错误码。码段 09xx，与 auth-service 已有子模块（01xx-08xx）不冲突。
 */
@Getter
@AllArgsConstructor
public enum PermissionMenuError implements ErrorDefinition {

  MENU_NOT_FOUND("SERVICE.AUTH.0901", "菜单节点不存在"),
  DUPLICATE_MENU_CODE("SERVICE.AUTH.0902", "菜单编码已存在"),
  MENU_HAS_CHILDREN("SERVICE.AUTH.0903", "菜单存在子节点，无法删除"),
  INVALID_MENU_CATEGORY("SERVICE.AUTH.0904", "菜单类别无效"),
  INVALID_MENU_VISIBILITY("SERVICE.AUTH.0905", "菜单可见性规则无效"),
  CANNOT_DISABLE_WITH_VISIBLE_CHILDREN("SERVICE.AUTH.0906", "存在可见子节点，无法禁用"),
  CIRCULAR_MOVE("SERVICE.AUTH.0907", "不能将菜单移动到其自身子节点下"),

  ;

  private final String code;
  private final String message;
}
