package com.example.file.domain.model.valueobject.config;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 路由匹配条件（维度=期望值，支持通配 *）.
 *
 * @param dimension    上下文维度（productNo / planNo / accountManager / channel 等）
 * @param expectedValue 期望值（"*" 通配任意非空）
 */
public record RouteCondition(String dimension, String expectedValue) implements ValueObject {

  public RouteCondition {
    if (dimension == null || dimension.isBlank()) {
      throw new IllegalArgumentException("RouteCondition.dimension empty");
    }
    if (expectedValue == null || expectedValue.isBlank()) {
      throw new IllegalArgumentException("RouteCondition.expectedValue empty");
    }
  }

  boolean matches(String actual) {
    if ("*".equals(expectedValue)) {
      return actual != null && !actual.isBlank();
    }
    return expectedValue.equals(actual);
  }
}
