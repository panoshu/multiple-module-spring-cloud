package com.example.shared.permission;

import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 从请求上下文解析当前登录账号 ID 的策略接口。
 *
 * <p>默认实现 {@link DefaultAccountIdResolver} 从 {@code X-Account-Id} 请求头取
 * （由网关在 sa-token 校验通过后写入）。业务服务可提供自定义 Bean 覆盖。
 *
 * @author shared-permission-starter
 */
public interface AccountIdResolver {

  /**
   * 解析当前登录账号 ID。
   *
   * @param joinPoint AOP 连接点（自定义实现可从方法入参提取信息）
   * @return 账号 ID，null 表示无法解析（切面将抛 BusinessException，fail-closed）
   */
  String resolve(ProceedingJoinPoint joinPoint);
}
