package com.example.shared.json.path;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSON 路径匹配器，从 JsonBodySanitizer 抽取的反向回溯匹配算法。
 *
 * <p>提供两个核心能力：
 * <ol>
 *   <li>{@link #buildIndex} — 将路径规则列表构建为叶子字段名索引，加速匹配</li>
 *   <li>{@link #match} — 基于路径栈反向回溯，判断字段是否匹配某条规则</li>
 * </ol>
 *
 * <p>匹配算法说明：
 * <ul>
 *   <li>精确匹配（{@code $.user.password}）：栈深度必须等于父级段数，逐段比对</li>
 *   <li>深度匹配（{@code $..password}）：栈深度 ≥ 父级段数，从栈顶向下比对</li>
 *   <li>路径扁平化：移除 {@code [*]}，对数组元素实施扁平化匹配（见下方说明）</li>
 * </ul>
 *
 * <p><b>{@code [*]} 行为说明</b>：表达式中的 {@code [*]} 会被移除，实施"路径扁平化"匹配。
 * 例如 {@code $.users[*].password} 会被处理为 {@code users.password}，导致以下两种结构都会匹配：
 * <pre>
 * {"users":[{"password":"123"}]}   // 数组场景，符合预期
 * {"users":{"password":"123"}}     // 对象场景，也会匹配（因为 [*] 被移除）
 * </pre>
 * 这是为日志脱敏场景设计的有意行为：无论 {@code users} 是数组还是对象，{@code password} 都应脱敏。
 * 如需严格区分数组和对象，不应使用 {@code [*]} 语法。
 *
 * @author trae
 * @since 1.0
 */
public final class PathMatcher {

  private PathMatcher() {
  }

  /**
   * 构建叶子字段名索引。
   *
   * <p>将多条路径规则按叶子字段名分组，后续匹配时先按叶子名命中索引，再遍历候选项。
   *
   * @param rules 路径规则列表（key=JsonPath 表达式，value=规则对象）
   * @param <T>   规则对象类型
   * @return 叶子字段名 → 规则列表 的索引 Map
   */
  public static <T> Map<String, List<RulePathEntry<T>>> buildIndex(Map<String, T> rules) {
    if (rules == null || rules.isEmpty()) {
      return Map.of();
    }
    Map<String, List<RulePathEntry<T>>> index = new java.util.HashMap<>();
    rules.forEach((path, rule) -> {
      if (path == null || path.isBlank()) {
        return;
      }
      String cleanPath = path;
      if (cleanPath.startsWith("$..")) {
        cleanPath = cleanPath.substring(3);
      } else if (cleanPath.startsWith("$.")) {
        cleanPath = cleanPath.substring(2);
      }
      cleanPath = cleanPath.replace("[*]", "");
      String[] segments = cleanPath.split("\\.");
      if (segments.length == 0) {
        return;
      }
      String leafName = segments[segments.length - 1];
      boolean isDeepScan = path.startsWith("$..");
      // 构建不可变的父级路径段列表（倒序：从叶子到根）
      List<String> parentSegments = new ArrayList<>(segments.length - 1);
      for (int i = 0; i < segments.length - 1; i++) {
        parentSegments.add(segments[segments.length - 2 - i]);
      }
      index.computeIfAbsent(leafName, k -> new ArrayList<>())
        .add(new RulePathEntry<>(new RulePath(leafName, List.copyOf(parentSegments), isDeepScan), rule));
    });
    return Map.copyOf(index);
  }

  /**
   * 在索引中查找匹配的规则。
   *
   * @param index     叶子字段名索引
   * @param leafName  当前叶子字段名
   * @param pathStack 路径栈（正序：root → parent）
   * @param <T>       规则对象类型
   * @return 匹配的规则对象；无匹配返回 null
   */
  public static <T> T match(Map<String, List<RulePathEntry<T>>> index, String leafName, List<String> pathStack) {
    List<RulePathEntry<T>> candidates = index.get(leafName);
    if (candidates == null) {
      return null;
    }
    for (RulePathEntry<T> candidate : candidates) {
      if (isPathMatch(pathStack, candidate.rulePath())) {
        return candidate.rule();
      }
    }
    return null;
  }

  /**
   * 反向回溯路径匹配算法。
   *
   * @param pathStack 路径栈（正序：root → parent）
   * @param candidate 候选规则路径
   * @return true 表示路径匹配
   */
  public static boolean isPathMatch(List<String> pathStack, RulePath candidate) {
    List<String> parents = candidate.parentSegments();
    int stackSize = pathStack.size();
    int parentCount = parents.size();

    if (!candidate.isDeepScan()) {
      if (stackSize != parentCount) {
        return false;
      }
    } else {
      if (stackSize < parentCount) {
        return false;
      }
    }

    for (int i = 0; i < parentCount; i++) {
      String ruleSegment = parents.get(i);
      String stackSegment = pathStack.get(stackSize - 1 - i);
      if (!ruleSegment.equals(stackSegment)) {
        return false;
      }
    }
    return true;
  }

  /**
   * 规则路径与规则对象的绑定。
   *
   * @param rulePath 规则路径
   * @param rule     规则对象
   * @param <T>      规则对象类型
   */
  public record RulePathEntry<T>(RulePath rulePath, T rule) {
  }
}
