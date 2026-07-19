package com.example.shared.logging.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * WithDurationLogging
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/23 22:42
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface WithDurationLogging {
  /**
   * 只有耗时超过该阈值（毫秒）时才记录日志，-1 表示总是记录
   */
  long threshold() default -1;

  /**
   * 日志级别，默认 INFO
   */
  String level() default "INFO";
}
