package com.pension.permission.domain.role.valueobject;

import com.pension.permission.domain.role.enumeration.RoleTemplateScopeDimension;
import com.pension.permission.domain.role.enumeration.RoleVisibilityMode;

/**
 * 角色可见性配置：dimension只应取PLAN或CUSTOMER(不像RoleTemplate那样还有PRODUCT/GLOBAL)，
 * 表示"这个计划/这个客户名下，分配经办时是否只展示专属角色"。
 */
public record RoleVisibilityScope(
  RoleTemplateScopeDimension dimension,
  String value, RoleVisibilityMode mode
) {
}
