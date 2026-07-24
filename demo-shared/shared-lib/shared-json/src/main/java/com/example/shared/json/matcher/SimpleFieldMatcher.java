package com.example.shared.json.matcher;

import java.util.List;
import java.util.Set;

/**
 * 简单字段名匹配器：基于字段名集合匹配，忽略路径。
 *
 * <p>适用于不需要区分字段路径的场景，如网关加解密：只要字段名在集合中就处理。
 *
 * <p>线程安全：内部使用不可变 {@link Set}。
 *
 * @author trae
 * @since 1.0
 */
public final class SimpleFieldMatcher implements FieldMatcher {

  private final Set<String> fieldNames;

  public SimpleFieldMatcher(Set<String> fieldNames) {
    this.fieldNames = Set.copyOf(fieldNames);
  }

  @Override
  public boolean match(String fieldName, List<String> pathStack) {
    return fieldNames.contains(fieldName);
  }
}
