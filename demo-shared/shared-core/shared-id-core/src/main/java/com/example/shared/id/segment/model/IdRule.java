package com.example.shared.id.segment.model;

import lombok.Builder;
import lombok.Data;

/**
 * ID 路由规则 (用于 Service 层)
 */
@Data
@Builder
public class IdRule {
  /**
   * 业务类型 (入口参数)
   */
  private String bizType;
  /**
   * 物理序列Key (映射目标)
   */
  private String sequenceKey;
}
