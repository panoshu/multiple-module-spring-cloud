package com.example.core.adapter.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 批次 48：fail-fast 开关 {@code core.kernel.production.fail-fast} 的装配语义契约。
 * <p>
 * 修正前 {@code @ConditionalOnProperty(havingValue="false")} 语义与 Javadoc 相反：
 * {@code =false} 反而装配（启用校验）、{@code =true} 才关闭。契约锁定修正后的直觉语义——
 * 缺省或 {@code =true} 启用、{@code =false} 关闭，且缺省行为不变（E2E 零回归）。
 */
@DisplayName("KernelProductionReadinessValidator 开关装配契约")
class KernelProductionReadinessConditionTest {

  private static final String PROPERTY = "core.kernel.production.fail-fast";

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
    .withUserConfiguration(KernelProductionReadinessValidator.class);

  @Test
  @DisplayName("缺省时启用校验（装配 bean，默认行为不变）")
  void default_enablesValidator() {
    runner.run(context ->
      assertThat(context).hasSingleBean(KernelProductionReadinessValidator.class));
  }

  @Test
  @DisplayName("显式 true 启用校验")
  void explicitTrue_enablesValidator() {
    runner.withPropertyValues(PROPERTY + "=true")
      .run(context -> assertThat(context).hasSingleBean(KernelProductionReadinessValidator.class));
  }

  @Test
  @DisplayName("显式 false 关闭校验（不装配 bean）")
  void explicitFalse_disablesValidator() {
    runner.withPropertyValues(PROPERTY + "=false")
      .run(context -> assertThat(context).doesNotHaveBean(KernelProductionReadinessValidator.class));
  }
}
