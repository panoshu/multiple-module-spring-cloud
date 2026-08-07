package com.example.auth.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式权限校验注解，由 shared-permission-starter 的 AOP 切面拦截。
 *
 * <p>使用示例：
 * <pre>{@code
 * @RequirePermission(business = "APPROVAL_FLOW", action = "CREATE")
 * public ApiResult<...> create(...) { ... }
 * }</pre>
 *
 * @author auth-api
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /**
     * 业务编码，对应 PermissionItem.businessCode
     */
    String business();

    /**
     * 操作编码，空串代表不区分操作
     */
    String action() default "";

    /**
     * 权限类别，默认业务权限
     */
    PermissionCategory category() default PermissionCategory.BUSINESS;
}
