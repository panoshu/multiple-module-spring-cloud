package com.example.shared.id.properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link IdProperties} 配置绑定测试。
 * <p>
 * 验证 {@code @ConfigurationProperties} 的 prefix 与实际配置前缀 {@code shared.id} 一致，
 * 避免路由规则因前缀不匹配（如误写为 {@code shared.identity}）而无法绑定。
 * <p>
 * 使用 {@link ApplicationContextRunner} 进行轻量级上下文测试，无需启动完整 SpringBoot 应用。
 *
 * @author panoshu
 * @since 2026/7/24
 */
@DisplayName("IdProperties 配置绑定测试")
class IdPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
    .withUserConfiguration(TestConfig.class)
    .withPropertyValues(
      "shared.id.rules.VIP_ORDER=GLOBAL_ORDER_SEQ",
      "shared.id.rules.NORMAL_ORDER=GLOBAL_ORDER_SEQ",
      "shared.id.rules.MY_LOAN=LOAN_SEQ_V2"
    );

  @Test
  @DisplayName("shared.id.rules 路由规则应能正确绑定")
  void should_bind_rules_from_shared_id_prefix() {
    runner.run(context -> {
      IdProperties props = context.getBean(IdProperties.class);

      assertThat(props.getRules())
        .as("shared.id.rules 应被正确绑定，prefix 必须为 shared.id")
        .isNotNull()
        .isNotEmpty();
    });
  }

  @Test
  @DisplayName("应包含 VIP_ORDER 到 GLOBAL_ORDER_SEQ 的映射")
  void should_contain_vip_order_rule() {
    runner.run(context -> {
      IdProperties props = context.getBean(IdProperties.class);

      assertThat(props.getRules())
        .containsEntry("VIP_ORDER", "GLOBAL_ORDER_SEQ");
    });
  }

  @Test
  @DisplayName("应包含 MY_LOAN 到 LOAN_SEQ_V2 的映射")
  void should_contain_my_loan_mapping() {
    runner.run(context -> {
      IdProperties props = context.getBean(IdProperties.class);

      assertThat(props.getRules())
        .containsEntry("MY_LOAN", "LOAN_SEQ_V2");
    });
  }

  @Test
  @DisplayName("validation 默认应启用")
  void should_enable_validation_by_default() {
    runner.run(context -> {
      IdProperties props = context.getBean(IdProperties.class);

      assertThat(props.getValidation()).isNotNull();
      assertThat(props.getValidation().isEnabled()).isTrue();
    });
  }

  @Configuration
  @EnableConfigurationProperties(IdProperties.class)
  static class TestConfig {
    // 仅用于启用 @ConfigurationProperties 绑定
  }
}
