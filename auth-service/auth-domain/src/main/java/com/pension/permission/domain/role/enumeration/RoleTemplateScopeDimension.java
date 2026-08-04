package com.pension.permission.domain.role.enumeration;

/**
 * 角色模板的适用范围维度。GLOBAL(全局默认)是显式取值，而不是null，便于优先级链读起来更直白。
 */
public enum RoleTemplateScopeDimension {
  GLOBAL, CUSTOMER, PRODUCT, PLAN
}
