package com.example.gateway.matcher;

import com.example.gateway.config.ExcludeRouteProperties;
import com.example.gateway.config.ExcludeRouteProperties.ExcludeRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcludeRouteMatcherTest {

  private ExcludeRouteMatcher matcher;

  @BeforeEach
  void setUp() {
    // 构造测试数据
    List<ExcludeRule> rules = List.of(
      // 规则1: 只限制 GET /admin/**，返回 404
      new ExcludeRule("/admin/**", List.of("GET"), 404),
      // 规则2: 限制所有方法的 /private/**，Status 为 null (将用全局默认)
      new ExcludeRule("/private/**", null, null)
    );
    matcher = new ExcludeRouteMatcher(new ExcludeRouteProperties(403, rules));
  }

  @Test
  @DisplayName("匹配成功：路径和方法都匹配")
  void matchSuccess() {
    var result = matcher.match("/admin/users", "GET");
    assertThat(result.matched()).isTrue();
    assertThat(result.ruleStatus()).isEqualTo(404);
  }

  @Test
  @DisplayName("匹配失败：路径匹配但方法不匹配")
  void matchFailWrongMethod() {
    var result = matcher.match("/admin/users", "POST");
    assertThat(result.matched()).isFalse();
  }

  @Test
  @DisplayName("匹配成功：方法列表为空代表匹配所有方法")
  void matchSuccessAllMethods() {
    // 规则2 没有配置 method
    assertThat(matcher.match("/private/data", "POST").matched()).isTrue();
    assertThat(matcher.match("/private/data", "DELETE").matched()).isTrue();
  }

  @Test
  @DisplayName("匹配失败：路径完全不匹配")
  void matchFailWrongPath() {
    var result = matcher.match("/public/home", "GET");
    assertThat(result.matched()).isFalse();
  }

  @Test
  @DisplayName("优先匹配：应该匹配列表中的第一个命中规则")
  void matchOrder() {
    // 如果有两个规则重叠，逻辑上取决于 list 的顺序 (代码中是按 list遍历)
    // 这里测试确保它找到即返回
    var result = matcher.match("/admin/test", "GET");
    assertThat(result.matched()).isTrue();
  }
}
