package com.example.shared.web.trace.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 业务上下文配置属性 (充血模型 )
 * * 职责：
 * 1. 承载 yaml 配置数据 (mapping)
 * 2. 提供业务语义的访问方法 (屏蔽 null 判断和别名逻辑)
 */
@Slf4j
@Validated
@ConfigurationProperties(prefix = "trace.context")
public class TraceContextProperties {

  // 派生状态：反向映射（小写headerKey -> alias）
  private final Map<String, String> reverseMapping = new HashMap<>();
  @NotEmpty
  private Map<String, @NotBlank String> mapping = new HashMap<>();

  /**
   * S-13：starter 不再携带 application.yml，默认业务头映射内聚到属性类型。
   * 宿主显式配置 {@code trace.context.mapping} 时覆盖业务别名；其中 {@code traceId}
   * 是链路追踪的基础设施字段，始终保留并统一映射到 {@code X-Trace-Id}，
   * 避免宿主覆盖 mapping 后 traceId 无法输出或透传。
   */
  public TraceContextProperties() {
    setMapping(Map.of(
      "order_id", "x-order-id",
      "user_id", "x-user-id",
      "traceId", "x-trace-id"));
  }

  public void setMapping(Map<String, String> mapping) {
    Objects.requireNonNull(mapping, "mapping must not be null");

    if (mapping.isEmpty()) {
      throw new IllegalArgumentException("trace.context.mapping must not be empty");
    }

    // traceId 为保留别名：移除宿主可能配置的等价项，再统一绑定标准头
    Map<String, String> effectiveMapping = new HashMap<>(mapping);
    effectiveMapping.entrySet().removeIf(entry ->
      "traceId".equalsIgnoreCase(entry.getKey())
        || "x-trace-id".equalsIgnoreCase(entry.getValue()));
    effectiveMapping.put("traceId", "x-trace-id");

    // 校验并构建双向映射
    Map<String, String> tempReverse = new HashMap<>();

    for (Map.Entry<String, String> entry : effectiveMapping.entrySet()) {
      String alias = entry.getKey();
      String headerKey = entry.getValue();

      if (alias == null || alias.isBlank() || headerKey == null || headerKey.isBlank()) {
        throw new IllegalArgumentException("Invalid mapping: " + entry);
      }

      // 校验唯一性（HTTP header 大小写不敏感，按小写归一）
      String normalizedHeaderKey = headerKey.toLowerCase(Locale.ROOT);
      if (tempReverse.containsKey(normalizedHeaderKey)) {
        throw new IllegalArgumentException(
          "Duplicate header key '%s' mapped by both '%s' and '%s'".formatted(
            headerKey, tempReverse.get(normalizedHeaderKey), alias
          )
        );
      }

      tempReverse.put(normalizedHeaderKey, alias);
    }

    this.mapping = Map.copyOf(effectiveMapping);
    this.reverseMapping.clear();
    this.reverseMapping.putAll(tempReverse);
  }

  /**
   * 根据 headerKey 获取 alias（O(1)，忽略大小写）
   */
  public String getAlias(String headerKey) {
    if (headerKey == null || headerKey.isBlank()) {
      return null;
    }
    return reverseMapping.getOrDefault(headerKey.toLowerCase(Locale.ROOT), headerKey);
  }

  /**
   * 根据 alias 获取 headerKey（正向查询，原值返回）
   */
  public String getHeaderKey(String alias) {
    return mapping.getOrDefault(alias, alias);
  }

  public Set<String> getHeaderKeys() {
    return Set.copyOf(reverseMapping.keySet());
  }

  public Set<String> getAliases() {
    return Set.copyOf(mapping.keySet());
  }

  public boolean hasAlias(String alias) {
    return mapping.containsKey(alias);
  }

  public boolean hasHeaderKey(String headerKey) {
    return getAlias(headerKey) != null;
  }
}
