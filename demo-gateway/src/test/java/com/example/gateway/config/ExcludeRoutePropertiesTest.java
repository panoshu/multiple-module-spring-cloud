package com.example.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcludeRoutePropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
    .withUserConfiguration(EnablePropsConfig.class); // 使用辅助配置类

  @Test
  @DisplayName("检测：YAML中的 rules 属性必须能绑定到 Java 对象")
  void testPropertiesBinding() {
    runner.withPropertyValues(
        "gateway.exclude-routes.default-status=403",
        // 确保这里使用的是 rules (如果你已经按上一轮建议修改了 Java 字段名为 rules)
        "gateway.exclude-routes.rules[0].path=/aaa/**",
        "gateway.exclude-routes.rules[0].methods=GET",
        "gateway.exclude-routes.rules[0].status=404"
      )
      .run(context -> {
        // 2. 断言上下文启动成功，且有名为 excludeRouteProperties 的 Bean
        assertThat(context).hasNotFailed();
        assertThat(context).hasSingleBean(ExcludeRouteProperties.class);

        ExcludeRouteProperties props = context.getBean(ExcludeRouteProperties.class);

        // 3. 验证值是否进去了
        assertThat(props.defaultStatus()).isEqualTo(403);

        assertThat(props.rules())
          .as("配置文件绑定失败，请检查 Java 字段名是否与 'rules' 一致")
          .isNotEmpty()
          .hasSize(1);

        assertThat(props.rules().getFirst().path()).isEqualTo("/aaa/**");
      });
  }

  // 4. 辅助配置类：专门用于开启 ConfigurationProperties 功能
  @EnableConfigurationProperties(ExcludeRouteProperties.class)
  static class EnablePropsConfig {}

  @Test
  @DisplayName("默认构造：列表为空时应初始化为空集合")
  void testDefaultConstructor() {
    var props = new ExcludeRouteProperties(403, null);
    assertThat(props.rules()).isNotNull().isEmpty();
  }

  @Test
  @DisplayName("规则校验：合法的 HTTP 方法和状态码应通过")
  void testValidRule() {
    var rule = new ExcludeRouteProperties.ExcludeRule(
      "/api/**",
      List.of("GET", "post "), // 测试大小写和空格清洗
      404
    );

    assertThat(rule.methods()).containsExactly("GET", "POST");
  }

  @Test
  @DisplayName("规则校验：无效的 HTTP 方法应抛出异常")
  void testInvalidHttpMethod() {
    assertThatThrownBy(() -> new ExcludeRouteProperties.ExcludeRule(
      "/api/**",
      List.of("INVALID_METHOD"),
      403
    )).isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Invalid HTTP methods");
  }

  @ParameterizedTest
  @ValueSource(ints = {999, 0, -1})
  @DisplayName("规则校验：无效的 HTTP 状态码应抛出异常")
  void testInvalidHttpStatus(int invalidStatus) {
    assertThatThrownBy(() -> new ExcludeRouteProperties.ExcludeRule(
      "/api/**",
      List.of("GET"),
      invalidStatus
    )).isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("Invalid HTTP status code");
  }

  @Test
  @DisplayName("规则校验：Status 为 null 是允许的 (将使用默认值)")
  void testNullStatusIsAllowed() {
    var rule = new ExcludeRouteProperties.ExcludeRule("/api/**", null, null);
    assertThat(rule.status()).isNull();
  }
}
