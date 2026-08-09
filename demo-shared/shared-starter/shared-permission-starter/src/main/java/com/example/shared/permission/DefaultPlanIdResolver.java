package com.example.shared.permission;

import com.example.auth.api.annotation.PermissionCategory;
import com.example.auth.api.annotation.RequirePermission;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 默认 planId 解析器：扫描方法入参，若实现 {@link PlanIdAware} 接口则取 {@code planId()}。
 *
 * <p>平台类权限（{@code category = PLATFORM}）不需要 planId，直接返回 null。
 * 找不到 {@link PlanIdAware} 入参时返回 null。
 *
 * @author shared-permission-starter
 */
public class DefaultPlanIdResolver implements PlanIdResolver {

  @Override
  public String resolve(ProceedingJoinPoint joinPoint, RequirePermission requirePermission) {
    if (requirePermission.category() == PermissionCategory.PLATFORM) {
      return null;
    }
    for (Object arg : joinPoint.getArgs()) {
      if (arg instanceof PlanIdAware aware) {
        return aware.planId();
      }
    }
    return null;
  }
}
