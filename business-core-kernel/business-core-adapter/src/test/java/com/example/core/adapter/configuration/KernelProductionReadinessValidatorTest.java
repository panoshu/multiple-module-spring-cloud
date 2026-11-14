package com.example.core.adapter.configuration;

import com.example.shared.permission.SessionContextSignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("KernelProductionReadinessValidator 生产就绪校验")
class KernelProductionReadinessValidatorTest {

  private static final String KEY_PROPERTY = "permission.session.signature-key";

  private Environment environment;
  private ObjectProvider<SessionContextSignatureVerifier> verifierProvider;
  private KernelProductionReadinessValidator validator;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    environment = mock(Environment.class);
    verifierProvider = mock(ObjectProvider.class);
    when(environment.getActiveProfiles()).thenReturn(new String[0]);
    validator = new KernelProductionReadinessValidator(environment, verifierProvider);
  }

  @Test
  @DisplayName("生产缺会话验签密钥时拒绝启动")
  void production_missingSignatureKeyFailsFast() {
    assertThatThrownBy(() -> validator.run(null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining(KEY_PROPERTY);
  }

  @Test
  @DisplayName("生产使用开发占位密钥时拒绝启动")
  void production_devPlaceholderKeyFailsFast() {
    when(environment.getProperty(KEY_PROPERTY)).thenReturn("dev-session-signature-key-2026");

    assertThatThrownBy(() -> validator.run(null))
      .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("生产缺验签器 bean 时拒绝启动")
  void production_missingVerifierFailsFast() {
    when(environment.getProperty(KEY_PROPERTY)).thenReturn("prod-secret-key-2026-abcdef");

    assertThatThrownBy(() -> validator.run(null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("SessionContextSignatureVerifier");
  }

  @Test
  @DisplayName("生产密钥与验签器齐备时通过")
  void production_readyPasses() {
    when(environment.getProperty(KEY_PROPERTY)).thenReturn("prod-secret-key-2026-abcdef");
    when(verifierProvider.getIfAvailable()).thenReturn(mock(SessionContextSignatureVerifier.class));

    assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("test/local/dev 等非生产 profile 跳过校验")
  void nonProductionProfiles_skipValidation() {
    when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});

    assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
  }
}
