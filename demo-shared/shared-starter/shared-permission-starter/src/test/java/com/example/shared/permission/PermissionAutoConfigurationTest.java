package com.example.shared.permission;

import com.example.shared.security.permission.PermissionCheckPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("PermissionAutoConfiguration 显式装配")
class PermissionAutoConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
    .withConfiguration(AutoConfigurations.of(PermissionAutoConfiguration.class));

  @Test
  @DisplayName("存在 PermissionCheckPort 时显式注册执行器、切面与数据范围解析器")
  void registersPermissionComponents_whenPortPresent() {
    runner.withBean(PermissionCheckPort.class, () -> mock(PermissionCheckPort.class))
      .run(context -> {
        assertThat(context).hasSingleBean(PermissionExecutor.class);
        assertThat(context).hasSingleBean(RequirePermissionAspect.class);
        assertThat(context).hasSingleBean(DataScopeResolver.class);
        assertThat(context).hasSingleBean(DataScopeAspect.class);
        assertThat(context).hasBean("httpExchangePermissionExecutor");
        assertThat(context).hasBean("permissionReadinessValidator");
      });
  }

  @Test
  @DisplayName("缺少 PermissionCheckPort 时不注册 HTTP 执行器与权限切面（由就绪校验兜底）")
  void doesNotRegisterHttpExecutor_withoutPort() {
    runner.run(context -> {
      assertThat(context).doesNotHaveBean(PermissionExecutor.class);
      assertThat(context).doesNotHaveBean(RequirePermissionAspect.class);
      assertThat(context).hasBean("permissionReadinessValidator");
    });
  }
}
