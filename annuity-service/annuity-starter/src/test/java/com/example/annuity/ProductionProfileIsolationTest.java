package com.example.annuity;

import com.example.core.adapter.configuration.KernelProductionReadinessValidator;
import com.example.shared.permission.SessionContextSignatureVerifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * P0-2 生产 profile 治理契约（批次 47）。
 * <p>
 * 主配置不得用 {@code spring.profiles.include} 无条件激活 {@code local}/{@code dev}/{@code test}：
 * 否则生产环境也带上非生产 profile，{@link KernelProductionReadinessValidator} 会误判并跳过
 * fail-fast，导致会话验签密钥缺失或使用开发占位值时静默降级。
 */
@DisplayName("annuity 生产 profile 隔离契约")
class ProductionProfileIsolationTest {

  @Test
  @DisplayName("主配置不无条件 include 非生产 profile")
  void mainConfig_doesNotIncludeNonProductionProfiles() throws IOException {
    YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
    List<PropertySource<?>> sources = loader.load("application", new ClassPathResource("application.yml"));

    String include = sources.stream()
      .map(source -> source.getProperty("spring.profiles.include"))
      .filter(java.util.Objects::nonNull)
      .map(Object::toString)
      .findFirst()
      .orElse("");

    assertThat(include).doesNotContain("local", "dev", "test");
  }

  @Test
  @DisplayName("prod profile 缺验签密钥时触发 fail-fast，不被非生产 profile 绕过")
  @SuppressWarnings("unchecked")
  void prodProfile_triggersFailFastWhenSignatureKeyMissing() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    ObjectProvider<SessionContextSignatureVerifier> verifierProvider = mock(ObjectProvider.class);
    KernelProductionReadinessValidator validator =
      new KernelProductionReadinessValidator(environment, verifierProvider);

    assertThatThrownBy(() -> validator.run(null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("permission.session.signature-key");
  }
}
