package com.example.shared.json.matcher;

import java.util.List;

/**
 * 字段匹配器接口：判断 JSON 遍历过程中遇到的字段是否需要处理。
 *
 * <p>解耦字段匹配逻辑与 JSON 遍历逻辑，支持不同匹配策略：
 * <ul>
 *   <li>{@link SimpleFieldMatcher} — 字段名集合匹配（忽略路径）</li>
 *   <li>{@link com.example.shared.json.matcher.JsonPathFieldMatcher} — JSON 路径匹配（支持嵌套路径校验）</li>
 * </ul>
 *
 * @author trae
 * @since 1.0
 */
public interface FieldMatcher {

  /**
   * 判断字段是否匹配。
   *
   * @param fieldName 当前叶子字段名
   * @param pathStack 从根到当前字段父级的路径栈（正序：root → parent，不含当前字段名）
   * @return true 表示该字段需要处理
   */
  boolean match(String fieldName, List<String> pathStack);
}
