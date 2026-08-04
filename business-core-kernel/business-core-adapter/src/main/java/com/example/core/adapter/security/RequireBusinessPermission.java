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
