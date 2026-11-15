package com.example.core.adapter.configuration;

import com.example.shared.permission.RequirePermissionAspect;
import com.example.shared.permission.SessionContextSignatureVerifier;
import com.example.shared.security.permission.PermissionCheckPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Set;

/**
 * kernel 生产就绪校验（spec §14，批次 38-1）。
 * <p>
 * 生产环境启动时必须已配置会话验签密钥且验签器可用，否则拒绝启动（fail-fast），
 * 避免功能权限与会话验签在运行时静默降级。test/local/dev 等开发环境跳过，
 * 保证 E2E 零回归。开关 {@code core.kernel.production.fail-fast}：缺省或 {@code =true}
 * 启用校验，{@code =false} 关闭校验（仅建议在受控环境临时关闭）。
 *
 * @author core-kernel
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "core.kernel.production.fail-fast", havingValue = "true", matchIfMissing = true)
public class KernelProductionReadinessValidator implements ApplicationRunner {

  private static final String SIGNATURE_KEY_PROPERTY = "permission.session.signature-key";
  private static final String DEV_PLACEHOLDER_PREFIX = "dev-";
  private static final Set<String> NON_PRODUCTION_PROFILES = Set.of("test", "local", "dev");

  private final Environment environment;
  private final ObjectProvider<SessionContextSignatureVerifier> verifierProvider;
  private final ObjectProvider<PermissionCheckPort> permissionCheckPortProvider;
  private final ObjectProvider<RequirePermissionAspect> requirePermissionAspectProvider;

  @Override
  public void run(ApplicationArguments args) {
    if (isNonProduction()) {
      log.debug("非生产 profile，跳过 kernel 生产就绪校验");
      return;
    }
    String signatureKey = environment.getProperty(SIGNATURE_KEY_PROPERTY);
    if (!StringUtils.hasText(signatureKey) || signatureKey.startsWith(DEV_PLACEHOLDER_PREFIX)) {
      throw new IllegalStateException(
        "生产环境缺少 " + SIGNATURE_KEY_PROPERTY + " 或仍为开发占位值，kernel 拒绝启动");
    }
    if (verifierProvider.getIfAvailable() == null) {
      throw new IllegalStateException(
        "生产环境缺少 SessionContextSignatureVerifier，kernel 拒绝启动");
    }
    // K-11：@RequirePermission 切面与权限客户端缺失时，所有 @RequirePermission 标注会静默失效。
    // 该 fail-fast 与 shared-permission-starter 的 PermissionReadinessValidator 双保险，
    // 但 kernel 宿主必须显式具备这两项能力，不能只依赖 shared starter 是否被引入。
    if (permissionCheckPortProvider.getIfAvailable() == null) {
      throw new IllegalStateException(
        "生产环境缺少 PermissionCheckPort（auth-service 权限客户端未装配），kernel 拒绝启动");
    }
    if (requirePermissionAspectProvider.getIfAvailable() == null) {
      throw new IllegalStateException(
        "生产环境缺少 RequirePermissionAspect（@RequirePermission 将静默失效），kernel 拒绝启动");
    }
    log.info("kernel 生产就绪校验通过");
  }

  private boolean isNonProduction() {
    return Arrays.stream(environment.getActiveProfiles())
      .anyMatch(NON_PRODUCTION_PROFILES::contains);
  }
}
