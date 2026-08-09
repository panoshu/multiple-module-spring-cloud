package com.example.shared.permission;

import com.example.auth.api.annotation.RequirePermission;
import com.example.shared.exception.BusinessException;
import com.example.shared.permission.errorcode.PermissionErrorCode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequirePermissionAspect 切面测试")
class RequirePermissionAspectTest {

  @Mock
  private PermissionExecutor permissionExecutor;

  @Mock
  private AccountIdResolver accountIdResolver;

  @Mock
  private PlanIdResolver planIdResolver;

  @Mock
  private ProceedingJoinPoint joinPoint;

  @Mock
  private Signature signature;

  private RequirePermissionAspect aspect;

  @BeforeEach
  void setUp() throws Exception {
    aspect = new RequirePermissionAspect(permissionExecutor, accountIdResolver, planIdResolver);
  }

  @Test
  @DisplayName("accountId 缺失时抛 SESSION_CONTEXT_MISSING")
  void throwsWhenAccountIdMissing() throws Throwable {
    RequirePermission annotation = sampleAnnotation();
    when(accountIdResolver.resolve(joinPoint)).thenReturn(null);

    assertThatThrownBy(() -> aspect.check(joinPoint, annotation))
      .isInstanceOf(BusinessException.class)
      .hasMessageContaining(PermissionErrorCode.SESSION_CONTEXT_MISSING.getMessage());

    verifyNoInteractions(permissionExecutor);
  }

  @Test
  @DisplayName("Executor 返回 allowed=true 时方法放行")
  void proceedsWhenExecutorAllows() throws Throwable {
    RequirePermission annotation = sampleAnnotation();
    when(accountIdResolver.resolve(joinPoint)).thenReturn("user-001");
    when(planIdResolver.resolve(joinPoint, annotation)).thenReturn("plan-001");
    when(permissionExecutor.check(any(PermissionCheckContext.class)))
      .thenReturn(PermissionCheckResult.allow());
    when(joinPoint.proceed()).thenReturn("ok");

    Object result = aspect.check(joinPoint, annotation);
    assertThat(result).isEqualTo("ok");
  }

  @Test
  @DisplayName("Executor 返回 allowed=false 时抛 PERMISSION_DENIED")
  void throwsWhenExecutorDenies() throws Throwable {
    RequirePermission annotation = sampleAnnotation();
    when(accountIdResolver.resolve(joinPoint)).thenReturn("user-001");
    when(planIdResolver.resolve(joinPoint, annotation)).thenReturn("plan-001");
    when(permissionExecutor.check(any(PermissionCheckContext.class)))
      .thenReturn(PermissionCheckResult.deny("权限不足"));

    assertThatThrownBy(() -> aspect.check(joinPoint, annotation))
      .isInstanceOf(BusinessException.class)
      .hasMessageContaining(PermissionErrorCode.PERMISSION_DENIED.getMessage());

    verify(joinPoint, never()).proceed();
  }

  @Test
  @DisplayName("Executor 抛异常时 fail-closed 抛 PERMISSION_SERVICE_UNAVAILABLE")
  void throwsWhenExecutorThrows() throws Throwable {
    RequirePermission annotation = sampleAnnotation();
    when(accountIdResolver.resolve(joinPoint)).thenReturn("user-001");
    when(planIdResolver.resolve(joinPoint, annotation)).thenReturn("plan-001");
    when(permissionExecutor.check(any(PermissionCheckContext.class)))
      .thenThrow(new RuntimeException("network error"));

    assertThatThrownBy(() -> aspect.check(joinPoint, annotation))
      .isInstanceOf(BusinessException.class)
      .hasMessageContaining(PermissionErrorCode.PERMISSION_SERVICE_UNAVAILABLE.getMessage());
  }

  private RequirePermission sampleAnnotation() throws Exception {
    Method method = RequirePermissionAspectTest.class.getDeclaredMethod("sampleMethod");
    return method.getAnnotation(RequirePermission.class);
  }

  @RequirePermission(business = "SAMPLE", action = "VIEW")
  void sampleMethod() {
    // 测试用占位
  }
}
