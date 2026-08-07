package com.example.shared.permission;

import com.example.auth.api.annotation.RequirePermission;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 从方法入参解析 planId 的策略接口。
 *
 * <p>默认实现 {@link DefaultPlanIdResolver} 扫描方法入参，
 * 若实现 {@link PlanIdAware} 接口则取 {@code planId()}。
 * 业务服务可提供自定义 Bean 覆盖。
 *
 * @author shared-permission-starter
 */
public interface PlanIdResolver {

  /**
   * 从切面连接点解析 planId。
   *
   * @param joinPoint         AOP 连接点
   * @param requirePermission 权限注解（可检查 category 决定是否需要 planId）
   * @return planId，null 表示无关联计划（平台类权限）
   */
  String resolve(ProceedingJoinPoint joinPoint, RequirePermission requirePermission);
}
