package com.example.core.domain.engine.aggregate.valueobject.enums.definition;

/**
 * 流程启动方式。
 */
public enum StartMode {
  DIRECT,       // 直接创建流程实例
  FORM_DRIVEN   // 由表单解析结果驱动创建流程实例
}
