package com.example.shared.permission;

import com.example.auth.api.annotation.PermissionCategory;
import com.example.auth.api.annotation.RequirePermission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DefaultPlanIdResolver} 单元测试。
 *
 * @author shared-permission-starter
 */
class DefaultPlanIdResolverTest {

  private final DefaultPlanIdResolver resolver = new DefaultPlanIdResolver();

  @Test
  void shouldResolvePlanIdFromPlanIdAwareArg() {
    ProceedingJoinPoint joinPoint = mockJoinPoint(new TestPlanIdAware("PLAN-001"));
    assertThat(resolver.resolve(joinPoint, mockBusinessAnnotation())).isEqualTo("PLAN-001");
  }

  @Test
  void shouldReturnNullWhenNoPlanIdAwareArg() {
    ProceedingJoinPoint joinPoint = mockJoinPoint("plain-string", 42);
    assertThat(resolver.resolve(joinPoint, mockBusinessAnnotation())).isNull();
  }

  @Test
  void shouldReturnNullWhenNoArgs() {
    ProceedingJoinPoint joinPoint = mockJoinPoint();
    assertThat(resolver.resolve(joinPoint, mockBusinessAnnotation())).isNull();
  }

  @Test
  void shouldResolveFromFirstPlanIdAwareWhenMultipleArgs() {
    ProceedingJoinPoint joinPoint = mockJoinPoint("plain-string",
      new TestPlanIdAware("PLAN-002"), new TestPlanIdAware("PLAN-003"));
    assertThat(resolver.resolve(joinPoint, mockBusinessAnnotation())).isEqualTo("PLAN-002");
  }

  @Test
  void shouldReturnNullWhenPlanIdAwareReturnsNull() {
    ProceedingJoinPoint joinPoint = mockJoinPoint(new TestPlanIdAware(null));
    assertThat(resolver.resolve(joinPoint, mockBusinessAnnotation())).isNull();
  }

  @Test
  void shouldReturnNullForPlatformCategory() {
    ProceedingJoinPoint joinPoint = mockJoinPoint(new TestPlanIdAware("PLAN-001"));
    assertThat(resolver.resolve(joinPoint, mockPlatformAnnotation())).isNull();
  }

  private RequirePermission mockBusinessAnnotation() {
    RequirePermission annotation = mock(RequirePermission.class);
    when(annotation.category()).thenReturn(PermissionCategory.BUSINESS);
    return annotation;
  }

  private RequirePermission mockPlatformAnnotation() {
    RequirePermission annotation = mock(RequirePermission.class);
    when(annotation.category()).thenReturn(PermissionCategory.PLATFORM);
    return annotation;
  }

  private ProceedingJoinPoint mockJoinPoint(Object... args) {
    ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
    when(joinPoint.getArgs()).thenReturn(args);
    return joinPoint;
  }

  private record TestPlanIdAware(String planId) implements PlanIdAware {
    @Override
    public String planId() {
      return planId;
    }
  }
}
