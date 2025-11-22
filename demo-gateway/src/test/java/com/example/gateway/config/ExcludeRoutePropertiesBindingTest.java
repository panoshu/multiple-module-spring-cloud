package com.example.gateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = { ExcludeRoutePropertiesBindingTest.Config.class })
// 这里模拟 application.yml 的结构
@TestPropertySource(properties = {
  "gateway.exclude-routes.default-status=403",
  // 关键点：这里故意模拟和 application.yml 一模一样的 key "rules"
  "gateway.exclude-routes.rules[0].path=/test/**",
  "gateway.exclude-routes.rules[0].methods[0]=GET",
  "gateway.exclude-routes.rules[0].status=404"
})
class ExcludeRoutePropertiesBindingTest {

  @Autowired
  private ExcludeRouteProperties properties;

  @Test
  @DisplayName("配置绑定测试：验证 YAML 中的 'rules' 能否正确映射到 Java 字段")
  void testBinding() {
    // 1. 验证基本属性
    assertThat(properties.defaultStatus()).isEqualTo(403);

    // 2. 验证核心列表 (如果字段名不匹配，这里就会断言失败)
    assertThat(properties.rules()) // 注意：如果你的 record 还没改名，这里可能是 .excludeRoutes()
      .isNotNull()
      .hasSize(1);

    var rule = properties.rules().getFirst();
    assertThat(rule.path()).isEqualTo("/test/**");
    assertThat(rule.status()).isEqualTo(404);
  }

  // 只需要加载这个配置类，不需要启动整个网关
  @EnableConfigurationProperties(ExcludeRouteProperties.class)
  static class Config {}
}
