package com.pension.permission.sdk;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明式标注需要的权限点，故意做成框架无关的纯注解——不管业务服务用Spring AOP、
 * 还是别的什么切面机制去解释它，都可以。planId通常从请求参数里解析，
 * 具体怎么从一个方法的入参里取出planId，由各服务自己的切面实现决定
 * (可以约定"第一个参数必须实现PlanIdAware"之类的规则，或者更简单地用SpEL，
 * 这些实现细节留给不同技术栈的团队自己决定，SDK不做假设)。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
  String business();

  /**
   * 不填代表不区分具体操作
   */
  String action() default "";
}
