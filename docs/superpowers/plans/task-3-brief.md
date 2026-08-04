# Task 3: 功能权限注解与 AOP 拦截器

**Files:**

- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/security/RequireBusinessPermission.java`
- Create:
  `business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/security/BusinessPermissionAspect.java`
- Test:
  `business-core-kernel/business-core-adapter/src/test/java/com/example/core/adapter/security/BusinessPermissionAspectTest.java`

**Interfaces:**

- Consumes: `SessionContextResolver`, `SessionContext`, `BusinessException`, `CommonError`
- Produces: `@RequireBusinessPermission` 注解, `BusinessPermissionAspect` bean

## Step 1: 编写 @RequireBusinessPermission 注解

```java
package com.example.core.adapter.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务功能权限校验注解
 *
 * <p>标注在 Controller 方法上,AOP 拦截器会校验当前会话用户的 {@code permissionCodes}
 * 是否包含指定权限码,用于垂直越权防护(功能权限)。
 *
 * <p>使用示例:
 * <pre>{@code
 * @PostMapping("/create")
 * @RequireBusinessPermission("BATCH_CREATE")
 * public ApiResult<BatchCreatedResponse> createBatch(...) { ... }
 * }</pre>
 *
 * <p>注意:业务类型办理权限(如 BUSINESS_ANNUITY_OPEN_HANDLE)属于数据权限范畴,
 * 由 {@link com.example.core.application.business.guard.BusinessAccessGuard} 校验。
 *
 * @author panoshu
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireBusinessPermission {

    /**
     * 需要的功能权限码,如 "BATCH_CREATE"、"FORM_UPLOAD"、"APPLICATION_SUBMIT"。
     */
    String value();
}
```

## Step 2: 编写 BusinessPermissionAspect 失败测试

> **设计决策**:Aspect 不再从方法参数找 `HttpServletRequest`,而是直接调用 `sessionContextResolver.require()`(内部通过
> `RequestContextHolder` 获取当前请求)。测试时通过 `RequestContextHolder.setRequestAttributes(...)` 设置模拟请求。

```java
package com.example.core.adapter.security;

import com.example.core.api.context.SessionContext;
import com.example.core.adapter.context.SessionContextResolver;
import com.example.shared.exception.BusinessException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * BusinessPermissionAspect 单元测试
 *
 * @author panoshu
 */
class BusinessPermissionAspectTest {

    private SessionContextResolver resolver;
    private BusinessPermissionAspect aspect;

    @BeforeEach
    void setUp() {
        resolver = mock(SessionContextResolver.class);
        aspect = new BusinessPermissionAspect(resolver);
        // 设置 RequestContextHolder,模拟 Web 请求上下文
        MockHttpServletRequest request = new MockHttpServletRequest();
        ServletRequestAttributes attrs = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(attrs);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void should_pass_when_permission_present() throws Throwable {
        when(resolver.require()).thenReturn(sessionWithPerms(Set.of("BATCH_CREATE")));
        ProceedingJoinPoint pjp = mockJoinPoint();

        Object result = aspect.checkPermission(pjp, "BATCH_CREATE");

        assertThat(result).isEqualTo("ok");
        verify(pjp).proceed();
    }

    @Test
    void should_fail_when_permission_missing() {
        when(resolver.require()).thenReturn(sessionWithPerms(Set.of()));
        ProceedingJoinPoint pjp = mockJoinPoint();

        assertThatThrownBy(() -> aspect.checkPermission(pjp, "BATCH_CREATE"))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("无功能权限");
    }

    private SessionContext sessionWithPerms(Set<String> perms) {
        return new SessionContext(
            "U001", "USER", "alice", "Alice",
            "INTERNET", "CLI001", "127.0.0.1",
            "C001", "Customer A",
            "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
            false, null, null, false, null, null,
            perms, Set.of()
        );
    }

    private ProceedingJoinPoint mockJoinPoint() {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(new Object[]{});
        try {
            when(pjp.proceed()).thenReturn("ok");
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        return pjp;
    }
}
```

## Step 3: 运行测试确认失败

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=BusinessPermissionAspectTest`
Expected: FAIL

## Step 4: 编写 BusinessPermissionAspect 实现

```java
package com.example.core.adapter.security;

import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.api.context.SessionContext;
import com.example.shared.exception.BusinessException;
import com.example.shared.exception.CommonError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 业务功能权限校验切面
 *
 * <p>拦截标注了 {@link RequireBusinessPermission} 的方法,通过 {@link SessionContextResolver}
 * 解析当前会话上下文(内部使用 {@code RequestContextHolder} 获取当前请求),
 * 校验 {@code permissionCodes} 是否包含注解声明的权限码。
 *
 * @author panoshu
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class BusinessPermissionAspect {

    private final SessionContextResolver sessionContextResolver;

    /**
     * 校验功能权限。
     */
    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequireBusinessPermission requirePermission) throws Throwable {
        String requiredCode = requirePermission.value();
        SessionContext session = sessionContextResolver.require();
        if (session.permissionCodes() == null || !session.permissionCodes().contains(requiredCode)) {
            throw new BusinessException(CommonError.FORBIDDEN)
                .withUserDetail("无功能权限")
                .withLogDetail("requiredPermission=%s, owned=%s".formatted(requiredCode, session.permissionCodes()));
        }
        return joinPoint.proceed();
    }
}
```

## Step 5: 在 adapter pom.xml 添加 AOP 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

## Step 6: 运行测试确认通过

Run: `mvn test -pl business-core-kernel/business-core-adapter -Dtest=BusinessPermissionAspectTest`
Expected: PASS (2 tests)

## Step 7: 提交

```bash
git add business-core-kernel/business-core-adapter/src/main/java/com/example/core/adapter/security/ \
        business-core-kernel/business-core-adapter/src/test/java/com/example/core/adapter/security/ \
        business-core-kernel/business-core-adapter/pom.xml
git commit -m "feat(core-adapter): 新增功能权限注解与 AOP 拦截器

1. @RequireBusinessPermission 注解标注在 Controller 方法上声明所需权限码
2. BusinessPermissionAspect 通过 AOP 拦截注解方法,校验会话用户的 permissionCodes
3. 用于垂直越权防护(功能权限),业务类型办理权限由 BusinessAccessGuard 校验"
```
