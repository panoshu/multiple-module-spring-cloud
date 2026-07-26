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

        Object result = aspect.checkPermission(pjp, mockPermission("BATCH_CREATE"));

        assertThat(result).isEqualTo("ok");
        verify(pjp).proceed();
    }

    @Test
    void should_fail_when_permission_missing() {
        when(resolver.require()).thenReturn(sessionWithPerms(Set.of()));
        ProceedingJoinPoint pjp = mockJoinPoint();

        assertThatThrownBy(() -> aspect.checkPermission(pjp, mockPermission("BATCH_CREATE")))
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).displayMessage())
            .asString()
            .contains("无功能权限");
    }

    private RequireBusinessPermission mockPermission(String value) {
        RequireBusinessPermission annotation = mock(RequireBusinessPermission.class);
        when(annotation.value()).thenReturn(value);
        return annotation;
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
