package com.pension.permission.domain.authorization.enumeration;

/**
 * 权限类别。区分业务权限（依赖 planId，走能力层+主体层）和平台管理权限
 * （不依赖 planId，仅走主体层 GLOBAL 匹配）。
 * <p>该类别只用于元数据层（PermissionItem、@RequirePermission 注解），
 * 不进入 Permission 值对象——Permission 的值语义只关心 (business, action) 二元组，
 * 类别不影响相等性判定。
 */
public enum PermissionCategory {
  BUSINESS(true),
  PLATFORM(false);

  private final boolean requiresPlan;

  PermissionCategory(boolean requiresPlan) {
    this.requiresPlan = requiresPlan;
  }

  public boolean requiresPlan() {
    return requiresPlan;
  }
}
