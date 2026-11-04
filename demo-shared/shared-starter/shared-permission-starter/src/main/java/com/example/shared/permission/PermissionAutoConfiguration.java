package com.example.shared.permission;

import com.example.shared.security.permission.PermissionCheckPort;
import com.example.shared.security.annotation.RequirePermission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * shared-permission-starter 自动装配入口.
 *
 * <p>装配条件：
 * <ul>
 *   <li>{@code @ConditionalOnClass(RequirePermission.class)} - auth-api 在 classpath</li>
 *   <li>{@code @ConditionalOnBean(PermissionCheckPort.class)} - 业务服务配置了 httpexchange 客户端</li>
 * </ul>
 *
 * <p>Phase C 后本地 Caffeine 缓存 + Phase D 事件失效/TTL 心跳已接线：
 * 权限变更经 {@code PermissionChangedEvent} 即时失效，TTL 300s 兜底。
 *
 * @author shared-permission-starter
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(RequirePermission.class)
@EnableConfigurationProperties({PermissionProperties.class, PermissionCacheProperties.class})
public class PermissionAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AccountIdResolver accountIdResolver(PermissionProperties properties) {
    return new DefaultAccountIdResolver(properties.getSession().getSignatureKey());
  }

  @Bean
  @ConditionalOnMissingBean
  public PlanIdResolver planIdResolver() {
    return new DefaultPlanIdResolver();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(prefix = "permission.session", name = "signature-key")
  public SessionContextSignatureVerifier sessionContextSignatureVerifier(
    PermissionProperties properties) {
    return new DefaultSessionContextSignatureVerifier(
      properties.getSession().getSignatureKey());
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(PermissionCheckPort.class)
  public RequirePermissionAspect requirePermissionAspect(
    PermissionExecutor permissionExecutor,
    AccountIdResolver accountIdResolver,
    PlanIdResolver planIdResolver) {
    return new RequirePermissionAspect(
      permissionExecutor, accountIdResolver, planIdResolver);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(DataScopeResolver.class)
  public DataScopeAspect dataScopeAspect(DataScopeResolver dataScopeResolver) {
    return new DataScopeAspect(dataScopeResolver);
  }

  /**
   * Phase D 失效接线：权限变更事件 → 本地缓存失效（spec §5 失效链消费端）.
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(HttpExchangePermissionExecutor.class)
  public PermissionCacheInvalidationListener permissionCacheInvalidationListener(
    HttpExchangePermissionExecutor executor) {
    return new PermissionCacheInvalidationListener(executor);
  }

  /**
   * Phase D 治理：权限自注册 TTL 心跳（重注册即续期，间隔可配，&le;0 禁用）.
   */
  @Bean(destroyMethod = "destroy")
  @ConditionalOnMissingBean
  @ConditionalOnBean(PermissionRegistrationRunner.class)
  public PermissionRegistrationRefresher permissionRegistrationRefresher(
    PermissionRegistrationRunner runner,
    org.springframework.core.env.Environment environment) {
    long interval = environment.getProperty(
      "shared.permission.registration.refresh-interval-ms", Long.class, 300_000L);
    return new PermissionRegistrationRefresher(runner, interval);
  }

  /**
   * P0-H2：跨服务失效通道收端——订阅 Redis topic {@code event.PermissionChangedEvent}
   * （auth 中继广播）→ 清本地 Caffeine.
   *
   * <p><b>嵌套配置类 + 字符串形式 {@code @ConditionalOnClass}（Spring Boot 规范）</b>：
   * spring-data-redis 是 optional 依赖，无 Redis 的宿主服务（如 annuity 测试上下文）
   * 不因外层类的类字面量引用触发 NoClassDefFoundError——外层类零 Redis 类型引用.
   */
  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(
    name = "org.springframework.data.redis.connection.RedisConnectionFactory")
  @ConditionalOnProperty(
    prefix = "shared.permission.invalidation.redis", name = "enabled",
    havingValue = "true", matchIfMissing = true)
  static class RedisInvalidationConfiguration {

    @Bean(destroyMethod = "destroy")
    @ConditionalOnMissingBean
    @ConditionalOnBean(
      org.springframework.data.redis.connection.RedisConnectionFactory.class)
    public PermissionInvalidationRedisSubscriber permissionInvalidationRedisSubscriber(
      org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory,
      PermissionCacheInvalidationListener invalidationListener,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
      return new PermissionInvalidationRedisSubscriber(
        connectionFactory, invalidationListener, objectMapper);
    }
  }
}
