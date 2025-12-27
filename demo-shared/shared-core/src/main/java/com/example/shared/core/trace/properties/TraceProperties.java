package com.example.shared.core.trace.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * 全链路追踪配置属性
 * 前缀: shared.trace
 */
@ConfigurationProperties(prefix = "shared.trace")
public record TraceProperties(
  // 是否开启整个 Trace 功能 (默认 true)
  boolean enable,

  // 是否开启服务端 Header 透传 Filter (默认 true)
  boolean enableFilter,

  // 自动识别为 bizId 的字段名集合
  Set<String> bizIdFields,

  // 自动识别为 batchId 的字段名集合
  Set<String> batchIdFields,

  // 自动识别为 jnlNo 的字段名集合
  Set<String> jnlNoFields
) {
  // 紧凑构造函数：提供默认值
  public TraceProperties {
    if (bizIdFields == null) bizIdFields = Set.of("bizId", "orderNo", "tradeNo", "orderId");
    if (batchIdFields == null) batchIdFields = Set.of("batchId", "batchNo");
    if (jnlNoFields == null) jnlNoFields = Set.of("jnlNo", "traceNo", "serialNo");
  }
}
