package com.example.shared.web.trace.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
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

  public void setMapping(Map<String, String> mapping) {
    Objects.requireNonNull(mapping, "mapping must not be null");

    if (mapping.isEmpty()) {
      throw new IllegalArgumentException("trace.context.mapping must not be empty");
    }

    // 校验并构建双向映射
    Map<String, String> tempReverse = new HashMap<>();

    for (Map.Entry<String, String> entry : mapping.entrySet()) {
      String alias = entry.getKey();
      String headerKey = entry.getValue();

      if (alias == null || alias.isBlank() || headerKey == null || headerKey.isBlank()) {
        throw new IllegalArgumentException("Invalid mapping: " + entry);
      }

      // 校验唯一性
      if (tempReverse.containsKey(headerKey)) {
        throw new IllegalArgumentException(
          "Duplicate header key '%s' mapped by both '%s' and '%s'".formatted(
            headerKey, tempReverse.get(headerKey), alias
          )
        );
      }

      tempReverse.put(headerKey, alias);
    }

    this.mapping = Map.copyOf(mapping);
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
    return reverseMapping.getOrDefault(headerKey, headerKey);
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
