package com.example.core.domain.vauleobject.config;

import java.util.Map;

/**
 * StepExtensionConfig
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/16 20:59
 */
public record StepExtensionConfig(
  String beanName,         // 动作实现类
  int order,               // 执行顺序
  String type,             // 动作类型：VALIDATION, ENRICHMENT, NOTIFICATION
  boolean isCritical,      // 失败是否阻断主流程
  boolean isAsync,         // 是否扔进线程池异步执行
  String condition,        // SpEL 表达式，满足才执行 (如: "#facts['age'] > 18")
  Map<String, Object> params // 自定义参数
) {
}
