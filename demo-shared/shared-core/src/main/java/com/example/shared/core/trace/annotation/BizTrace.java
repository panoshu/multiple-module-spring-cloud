package com.example.shared.core.trace.annotation;

import java.lang.annotation.*;

/**
 * 业务链路追踪注解
 * 用于在 Controller 方法上手动指定业务 ID 的来源
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BizTrace {
  /**
   * 业务 ID 的 SpEL 表达式
   * 例: "#req.orderNo"
   */
  String bizId() default "";

  /**
   * 批次号 SpEL
   */
  String batchId() default "";

  /**
   * 流水号 SpEL
   */
  String jnlNo() default "";
}
