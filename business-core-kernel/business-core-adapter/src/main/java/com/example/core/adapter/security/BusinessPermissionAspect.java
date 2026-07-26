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
