package com.example.shared.json.path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PathMatcher} 单元测试。
 *
 * @author trae
 * @since 1.0
 */
@DisplayName("PathMatcher 路径匹配测试")
class PathMatcherTest {

  @Test
  @DisplayName("buildIndex 应正确构建叶子字段名索引")
  void shouldBuildIndex() {
    Map<String, String> rules = new HashMap<>();
    rules.put("$.user.password", "RULE_USER_PWD");
    rules.put("$..password", "RULE_DEEP_PWD");

    Map<String, List<PathMatcher.RulePathEntry<String>>> index = PathMatcher.buildIndex(rules);

    assertThat(index).containsKey("password");
    assertThat(index.get("password")).hasSize(2);
  }

  @Test
  @DisplayName("buildIndex 空输入应返回空 Map")
  void shouldReturnEmptyForEmptyInput() {
    Map<String, List<PathMatcher.RulePathEntry<String>>> index = PathMatcher.buildIndex(Map.of());
    assertThat(index).isEmpty();

    assertThat(PathMatcher.buildIndex(null)).isEmpty();
  }

  @Test
  @DisplayName("buildIndex 应忽略 null 和空白 key")
  void shouldIgnoreBlankKeys() {
    Map<String, String> rules = new HashMap<>();
    rules.put(null, "RULE_NULL");
    rules.put("", "RULE_EMPTY");
    rules.put("  ", "RULE_BLANK");
    rules.put("$.valid", "RULE_VALID");

    Map<String, List<PathMatcher.RulePathEntry<String>>> index = PathMatcher.buildIndex(rules);

    assertThat(index).containsOnlyKeys("valid");
  }

  @Test
  @DisplayName("精确匹配：路径栈深度必须等于父级段数")
  void exactMatchShouldRequireExactDepth() {
    Map<String, String> rules = Map.of("$.user.password", "RULE");
    Map<String, List<PathMatcher.RulePathEntry<String>>> index = PathMatcher.buildIndex(rules);

    // 路径栈 [user] 匹配 $.user.password
    assertThat(PathMatcher.match(index, "password", List.of("user"))).isEqualTo("RULE");

    // 路径栈 [info, user] 不匹配（深度不符）
    assertThat(PathMatcher.match(index, "password", List.of("info", "user"))).isNull();

    // 路径栈 [] 不匹配（深度不符）
    assertThat(PathMatcher.match(index, "password", List.of())).isNull();
  }

  @Test
  @DisplayName("深度匹配：$..password 应匹配任意深度")
  void deepScanShouldMatchAnyDepth() {
    Map<String, String> rules = Map.of("$..password", "RULE");
    Map<String, List<PathMatcher.RulePathEntry<String>>> index = PathMatcher.buildIndex(rules);

    assertThat(PathMatcher.match(index, "password", List.of())).isEqualTo("RULE");
    assertThat(PathMatcher.match(index, "password", List.of("user"))).isEqualTo("RULE");
    assertThat(PathMatcher.match(index, "password", List.of("user", "info"))).isEqualTo("RULE");
  }

  @Test
  @DisplayName("深度匹配带父级：$..info.password 应匹配 info 下的 password")
  void deepScanWithParentShouldMatchParentPath() {
    Map<String, String> rules = Map.of("$..info.password", "RULE");
    Map<String, List<PathMatcher.RulePathEntry<String>>> index = PathMatcher.buildIndex(rules);

    // [user, info] 匹配
    assertThat(PathMatcher.match(index, "password", List.of("user", "info"))).isEqualTo("RULE");
    // [admin, info] 匹配
    assertThat(PathMatcher.match(index, "password", List.of("admin", "info"))).isEqualTo("RULE");
    // [user] 不匹配（深度不足）
    assertThat(PathMatcher.match(index, "password", List.of("user"))).isNull();
    // [user, data] 不匹配（父级段不匹配）
    assertThat(PathMatcher.match(index, "password", List.of("user", "data"))).isNull();
  }

  @Test
  @DisplayName("未索引的字段名应返回 null")
  void shouldReturnNullForUnindexedField() {
    Map<String, String> rules = Map.of("$.user.password", "RULE");
    Map<String, List<PathMatcher.RulePathEntry<String>>> index = PathMatcher.buildIndex(rules);

    assertThat(PathMatcher.match(index, "username", List.of("user"))).isNull();
  }

  @Test
  @DisplayName("isPathMatch 精确模式深度校验")
  void isPathMatchExactMode() {
    RulePath rulePath = new RulePath("password", List.of("user"), false);

    assertThat(PathMatcher.isPathMatch(List.of("user"), rulePath)).isTrue();
    assertThat(PathMatcher.isPathMatch(List.of("admin"), rulePath)).isFalse();
    assertThat(PathMatcher.isPathMatch(List.of("info", "user"), rulePath)).isFalse();
    assertThat(PathMatcher.isPathMatch(List.of(), rulePath)).isFalse();
  }

  @Test
  @DisplayName("isPathMatch 深度扫描模式深度校验")
  void isPathMatchDeepScanMode() {
    RulePath rulePath = new RulePath("password", List.of("info"), true);

    assertThat(PathMatcher.isPathMatch(List.of("info"), rulePath)).isTrue();
    assertThat(PathMatcher.isPathMatch(List.of("user", "info"), rulePath)).isTrue();
    assertThat(PathMatcher.isPathMatch(List.of("user"), rulePath)).isFalse();
    assertThat(PathMatcher.isPathMatch(List.of(), rulePath)).isFalse();
  }

  @Test
  @DisplayName("RulePath 应为深度不可变")
  void rulePathShouldBeDeeplyImmutable() {
    RulePath rulePath = new RulePath("password", List.of("user", "info"), false);

    // parentSegments 返回不可变 List，修改应抛 UnsupportedOperationException
    assertThatThrownBy(() -> rulePath.parentSegments().add("hack"))
      .isInstanceOf(UnsupportedOperationException.class);
  }
}
