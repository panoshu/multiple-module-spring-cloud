package com.example.shared.permission;

import com.example.shared.security.permission.PermissionCheckPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;

/**
 * 权限装配生产就绪校验（fail-fast）.
 *
 * <p><b>背景</b>：权限切面依赖 {@link PermissionCheckPort} / {@link PermissionExecutor}
 * 等 Bean 条件装配。若 httpexchange 客户端未创建或签名密钥缺失，切面会静默不装配，
 * {@code @RequirePermission} 全部失效——这是不可接受的 fail-open。</p>
 *
 * <p>校验规则（非 test/local/dev 环境且未显式关闭 enforcement）：
 * <ol>
 *   <li>{@code permission.session.signature-key} 必须存在且不是 {@code dev-} 占位值；</li>
 *   <li>{@link PermissionCheckPort} Bean 必须存在；</li>
 *   <li>{@link PermissionExecutor} Bean 必须存在；</li>
 *   <li>{@link RequirePermissionAspect} Bean 必须存在。</li>
 * </ol>
 *
 * <p>auth-service 自身使用 {@code LocalPermissionExecutor}，不依赖 HTTP 权限客户端，
 * 由应用名排除。</p>
 *
 * @author shared-permission-starter
 */
@Slf4j
@RequiredArgsConstructor
public class PermissionReadinessValidator implements ApplicationRunner {

  private static final String SIGNATURE_KEY_PROPERTY = "permission.session.signature-key";
  private static final String ENFORCEMENT_ENABLED_PROPERTY = "permission.enforcement.enabled";
  private static final String DEV_PLACEHOLDER_PREFIX = "dev-";
  private static final Set<String> NON_PRODUCTION_PROFILES = Set.of("test", "local", "dev");

  private final Environment environment;
  private final ObjectProvider<PermissionCheckPort> permissionCheckPort;
  private final ObjectProvider<PermissionExecutor> permissionExecutor;
  private final ObjectProvider<RequirePermissionAspect> requirePermissionAspect;

  @Override
  public void run(ApplicationArguments args) {
    if (!isEnforcementEnabled()) {
      log.warn("[PermissionReadiness] permission.enforcement.enabled=false，跳过权限就绪校验");
      return;
    }
    if (isNonProduction()) {
      log.debug("[PermissionReadiness] 非生产 profile，跳过权限就绪校验");
      return;
    }
    if (isAuthService()) {
      log.debug("[PermissionReadiness] auth-service 使用本地权限执行器，跳过 HTTP 权限客户端校验");
      return;
    }

    String signatureKey = environment.getProperty(SIGNATURE_KEY_PROPERTY);
    if (!StringUtils.hasText(signatureKey) || signatureKey.startsWith(DEV_PLACEHOLDER_PREFIX)) {
      throw new IllegalStateException(
        "[PermissionReadiness] 生产环境缺少 " + SIGNATURE_KEY_PROPERTY
          + " 或仍为开发占位值，权限装配拒绝启动");
    }
    if (permissionCheckPort.getIfAvailable() == null) {
      throw new IllegalStateException(
        "[PermissionReadiness] 缺少 PermissionCheckPort：请检查 http-exchange 客户端配置，"
          + "否则 @RequirePermission 将静默失效");
    }
    if (permissionExecutor.getIfAvailable() == null) {
      throw new IllegalStateException(
        "[PermissionReadiness] 缺少 PermissionExecutor：权限切面无法装配，拒绝启动");
    }
    if (requirePermissionAspect.getIfAvailable() == null) {
      throw new IllegalStateException(
        "[PermissionReadiness] 缺少 RequirePermissionAspect：@RequirePermission 将静默失效，拒绝启动");
    }
    log.info("[PermissionReadiness] 权限装配生产就绪校验通过");
  }

  private boolean isEnforcementEnabled() {
    return environment.getProperty(ENFORCEMENT_ENABLED_PROPERTY, Boolean.class, true);
  }

  private boolean isNonProduction() {
    return Arrays.stream(environment.getActiveProfiles())
      .anyMatch(NON_PRODUCTION_PROFILES::contains);
  }

  private boolean isAuthService() {
    return "auth-service".equals(environment.getProperty("spring.application.name"));
  }
}
