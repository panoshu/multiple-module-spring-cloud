package com.example.shared.web.core.annotation;

import java.lang.annotation.*;

/**
 * 业务上下文 ID 注解（S-15：窄用途注解）。
 *
 * <p>标记在 DTO 字段、Record 组件或 Controller 参数上，供
 * {@code shared-web-core} 的 {@code BizContextAspect} 提取链路业务标识。
 * 它不是通用参数校验/文档注解；新业务 DTO 无需普遍标注，只有需要进入 MDC 的
 * 关键标识（如订单号、用户号）才使用。
 *
 * <p>包名位于 shared-api 是历史兼容选择；本注解依赖 shared-web-core 的解释器，
 * 不应被非 Web 模块当作独立契约使用。
 */
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface BizTag {
  /**
   * 业务 ID 别名 (需在配置文件中映射到具体 Header)
   * 例如："order_id"
   */
  String value();
}
