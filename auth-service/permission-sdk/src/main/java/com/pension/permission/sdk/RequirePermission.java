package com.pension.permission.sdk;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式标注需要的权限点，故意做成框架无关的纯注解——不管业务服务用Spring AOP、
 * 还是别的什么切面机制去解释它，都可以。planId通常从请求参数里解析，
 * 具体怎么从一个方法的入参里取出planId，由各服务自己的切面实现决定。
 * <p>
 * {@code category} 区分业务权限（需要 planId，走能力层+主体层）和平台管理权限
 * （不需要 planId，仅走主体层 GLOBAL 匹配）。默认 BUSINESS 向后兼容。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
  String business();

  /**
   * 不填代表不区分具体操作
   */
  String action() default "";

  /**
   * 权限类别，默认业务权限
   */
  PermissionCategory category() default PermissionCategory.BUSINESS;
}
