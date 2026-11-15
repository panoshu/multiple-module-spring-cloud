package com.example.shared.permission;

import com.example.shared.security.permission.PermissionCheckPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("PermissionReadinessValidator 权限装配 fail-fast")
class PermissionReadinessValidatorTest {

  private final ObjectProvider<PermissionCheckPort> portProvider = mockProvider();
  private final ObjectProvider<PermissionExecutor> executorProvider = mockProvider();
  private final ObjectProvider<RequirePermissionAspect> aspectProvider = mockProvider();

  @Test
  @DisplayName("测试/local/dev profile 跳过校验")
  void nonProductionProfile_skips() {
    MockEnvironment env = environment("local");
    env.setProperty("spring.application.name", "file-service");

    assertThatCode(() -> validator(env).run(null)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("显式关闭 enforcement 时跳过校验")
  void enforcementDisabled_skips() {
    MockEnvironment env = environment("prod");
    env.setProperty("permission.enforcement.enabled", "false");

    assertThatCode(() -> validator(env).run(null)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("生产环境缺少签名密钥时拒绝启动")
  void production_missingSignatureKey_fails() {
    MockEnvironment env = environment("prod");
    env.setProperty("spring.application.name", "file-service");

    assertThatThrownBy(() -> validator(env).run(null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("signature-key");
  }

  @Test
  @DisplayName("生产环境缺少权限客户端时拒绝启动")
  void production_missingPort_fails() {
    MockEnvironment env = productionEnvironment();
    when(portProvider.getIfAvailable()).thenReturn(null);

    assertThatThrownBy(() -> validator(env).run(null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("PermissionCheckPort");
  }

  @Test
  @DisplayName("生产环境缺少权限执行器时拒绝启动")
  void production_missingExecutor_fails() {
    MockEnvironment env = productionEnvironment();
    when(portProvider.getIfAvailable()).thenReturn(mock(PermissionCheckPort.class));
    when(executorProvider.getIfAvailable()).thenReturn(null);

    assertThatThrownBy(() -> validator(env).run(null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("PermissionExecutor");
  }

  @Test
  @DisplayName("生产环境缺少权限切面时拒绝启动")
  void production_missingAspect_fails() {
    MockEnvironment env = productionEnvironment();
    when(portProvider.getIfAvailable()).thenReturn(mock(PermissionCheckPort.class));
    when(executorProvider.getIfAvailable()).thenReturn(mock(PermissionExecutor.class));
    when(aspectProvider.getIfAvailable()).thenReturn(null);

    assertThatThrownBy(() -> validator(env).run(null))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("RequirePermissionAspect");
  }

  @Test
  @DisplayName("生产环境装配完整时校验通过")
  void production_complete_passes() {
    MockEnvironment env = productionEnvironment();
    when(portProvider.getIfAvailable()).thenReturn(mock(PermissionCheckPort.class));
    when(executorProvider.getIfAvailable()).thenReturn(mock(PermissionExecutor.class));
    when(aspectProvider.getIfAvailable()).thenReturn(mock(RequirePermissionAspect.class));

    assertThatCode(() -> validator(env).run(null)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("auth-service 自身跳过 HTTP 权限客户端校验")
  void authService_skips() {
    MockEnvironment env = environment("prod");
    env.setProperty("spring.application.name", "auth-service");

    assertThatCode(() -> validator(env).run(null)).doesNotThrowAnyException();
  }

  private PermissionReadinessValidator validator(MockEnvironment env) {
    return new PermissionReadinessValidator(
      env, portProvider, executorProvider, aspectProvider);
  }

  private MockEnvironment productionEnvironment() {
    MockEnvironment env = environment("prod");
    env.setProperty("spring.application.name", "file-service");
    env.setProperty("permission.session.signature-key", "prod-signature-key");
    return env;
  }

  private MockEnvironment environment(String profile) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(profile);
    return env;
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> mockProvider() {
    return mock(ObjectProvider.class);
  }
}
