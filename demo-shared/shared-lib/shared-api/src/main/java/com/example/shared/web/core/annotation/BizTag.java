package com.example.shared.web.core.annotation;

import java.lang.annotation.*;

/**
 * 业务上下文 ID 注解
 * 标记在 DTO 的字段或 Record 的属性上，用于创建场景下的自动提取
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
