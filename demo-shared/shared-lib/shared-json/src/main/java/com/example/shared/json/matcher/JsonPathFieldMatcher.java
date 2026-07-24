package com.example.shared.json.matcher;

import com.example.shared.json.path.PathMatcher;
import com.example.shared.json.path.RulePath;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JSON 路径字段匹配器：基于 JsonPath 表达式匹配，支持嵌套路径校验和深度扫描。
 *
 * <p>从 JsonBodySanitizer 的路径匹配算法抽取，适用于需要区分字段路径的场景，
 * 如日志脱敏：只脱敏 {@code $.user.password} 而不脱敏 {@code $.admin.password}。
 *
 * <p>路径表达式语法：
 * <ul>
 *   <li>{@code $.user.info.password} — 精确路径匹配</li>
 *   <li>{@code $..password} — 深度扫描（递归下降）</li>
 *   <li>{@code $.items[*].password} — 数组通配（路径扁平化匹配）</li>
 * </ul>
 *
 * <p>线程安全：内部索引为不可变 Map。
 *
 * @author trae
 * @since 1.0
 */
public final class JsonPathFieldMatcher implements FieldMatcher {

  private final Map<String, List<PathMatcher.RulePathEntry<Boolean>>> index;

  /**
   * 构造路径匹配器。
   *
   * @param jsonPaths JsonPath 表达式集合（如 {@code $.user.password}、{@code $..password}）
   */
  public JsonPathFieldMatcher(Set<String> jsonPaths) {
    Map<String, Boolean> pathMap = new java.util.HashMap<>();
    for (String path : jsonPaths) {
      pathMap.put(path, Boolean.TRUE);
    }
    this.index = PathMatcher.buildIndex(pathMap);
  }

  @Override
  public boolean match(String fieldName, List<String> pathStack) {
    Boolean matched = PathMatcher.match(index, fieldName, pathStack);
    return Boolean.TRUE.equals(matched);
  }
}
