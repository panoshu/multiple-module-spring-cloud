package com.example.shared.permission;

import com.example.auth.api.annotation.RequirePermission;
import com.example.shared.exception.BusinessException;
import com.example.shared.permission.errorcode.PermissionErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * {@link RequirePermission} 注解的 AOP 切面.
 *
 * <p>通过 {@link PermissionExecutor} 抽象进行权限校验，业务服务走 HttpExchange，
 * auth-service 走本地短路。fail-closed：任何异常情况都拒绝访问。
 *
 * @author shared-permission-starter
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@Order(1)
public class RequirePermissionAspect {

    private final PermissionExecutor permissionExecutor;
    private final AccountIdResolver accountIdResolver;
    private final PlanIdResolver planIdResolver;

    @Around("@annotation(requirePermission)")
    public Object check(ProceedingJoinPoint joinPoint, RequirePermission requirePermission)
            throws Throwable {
        String accountId = accountIdResolver.resolve(joinPoint);
        if (accountId == null || accountId.isBlank()) {
            throw new BusinessException(PermissionErrorCode.SESSION_CONTEXT_MISSING)
                .withLogDetail("X-Account-Id header 缺失或验签失败")
                .withContext("business", requirePermission.business())
                .withContext("action", requirePermission.action());
        }

        String planId = planIdResolver.resolve(joinPoint, requirePermission);
        String businessCode = requirePermission.business();
        String actionCode = requirePermission.action().isBlank()
            ? null : requirePermission.action();

        PermissionCheckContext context = new PermissionCheckContext(
            accountId, planId, businessCode, actionCode);

        PermissionCheckResult result;
        try {
            result = permissionExecutor.check(context);
        } catch (Exception e) {
            log.warn("[RequirePermission] 权限校验失败, fail-closed. account={}, business={}",
                accountId, businessCode, e);
            throw new BusinessException(PermissionErrorCode.PERMISSION_SERVICE_UNAVAILABLE, e)
                .withLogDetail("权限校验执行异常: " + e.getMessage())
                .withContext("account", accountId)
                .withContext("business", businessCode);
        }

        if (result == null || !result.allowed()) {
            throw new BusinessException(PermissionErrorCode.PERMISSION_DENIED)
                .withContext("account", accountId)
                .withContext("plan", planId)
                .withContext("business", businessCode)
                .withContext("action", actionCode);
        }
        return joinPoint.proceed();
    }
}
