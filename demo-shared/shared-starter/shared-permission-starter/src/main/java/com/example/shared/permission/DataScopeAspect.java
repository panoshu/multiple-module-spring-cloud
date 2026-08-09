package com.example.shared.permission;

import com.example.auth.api.annotation.DataScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@link DataScope} 注解的 AOP 切面.
 *
 * <p>拦截标注 {@code @DataScope} 的方法，调用 {@link DataScopeResolver} 解析可见范围后
 * 放入 {@link DataScopeContext}，方法返回时清理 ThreadLocal。
 *
 * <p>切面顺序：在 {@link RequirePermissionAspect}（@Order(1)）之后执行，
 * 确保 @RequirePermission 先做功能权限校验，@DataScope 再设置可见范围。
 *
 * @author shared-permission-starter
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@Order(2)
public class DataScopeAspect {

  private final DataScopeResolver dataScopeResolver;

  @Around("@annotation(dataScope)")
  public Object applyDataScope(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
    try {
      com.example.auth.api.dto.DataScope scope = dataScopeResolver.resolve(dataScope.business());
      DataScopeContext.set(scope);
      return joinPoint.proceed();
    } finally {
      DataScopeContext.clear();
    }
  }
}
