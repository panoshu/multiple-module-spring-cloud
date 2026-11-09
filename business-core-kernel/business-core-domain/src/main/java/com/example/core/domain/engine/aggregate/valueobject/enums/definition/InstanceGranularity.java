package com.example.core.domain.engine.aggregate.valueobject.enums.definition;

/**
 * 表单驱动业务的流程实例裂变粒度。
 */
public enum InstanceGranularity {
  SINGLE,        // 一个表单创建一个流程实例
  PER_SUB_TASK   // 一个表单按子任务裂变多个流程实例
}
