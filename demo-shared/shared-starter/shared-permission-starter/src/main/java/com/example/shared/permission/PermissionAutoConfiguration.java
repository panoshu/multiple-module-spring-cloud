package com.example.shared.permission;

import com.example.shared.security.permission.PermissionCheckPort;
import com.example.shared.security.permission.PermissionRegistrationPort;
import com.example.shared.security.annotation.RequirePermission;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

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

  /**
   * 业务服务默认权限执行器：本地 Caffeine + HttpExchange 调用 auth-service.
   *
   * <p>此前该类仅标注 {@code @Component}，但 starter 包不会被宿主组件扫描，
   * 导致 {@code PermissionExecutor} 缺失、权限切面静默不装配。改为自动配置显式注册。</p>
   */
  @Bean
  @ConditionalOnBean(PermissionCheckPort.class)
  @ConditionalOnMissingBean(PermissionExecutor.class)
  public HttpExchangePermissionExecutor httpExchangePermissionExecutor(
    PermissionCheckPort permissionCheckPort,
    PermissionCacheProperties cacheProperties) {
    return new HttpExchangePermissionExecutor(permissionCheckPort, cacheProperties);
  }

  /**
   * 业务服务默认数据范围解析器（auth 侧由 LocalDataScopeResolver 覆盖）.
   */
  @Bean
  @ConditionalOnBean({PermissionCheckPort.class, AccountIdResolver.class})
  @ConditionalOnMissingBean(DataScopeResolver.class)
  public DefaultDataScopeResolver defaultDataScopeResolver(
    PermissionCheckPort permissionCheckPort,
    AccountIdResolver accountIdResolver) {
    return new DefaultDataScopeResolver(permissionCheckPort, accountIdResolver);
  }

  /**
   * 功能权限切面.
   *
   * <p>{@code permission.enforcement.enabled=false} 时整体关闭权限校验，
   * 仅用于本地/联调环境（无 auth-service 或批量服务启动）——生产环境必须保持默认开启，
   * 不得在 prod 显式关闭（{@link PermissionReadinessValidator} 会 fail-fast）.</p>
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(PermissionExecutor.class)
  @ConditionalOnProperty(
    prefix = "permission.enforcement", name = "enabled",
    havingValue = "true", matchIfMissing = true)
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
  @ConditionalOnProperty(
    prefix = "permission.enforcement", name = "enabled",
    havingValue = "true", matchIfMissing = true)
  public DataScopeAspect dataScopeAspect(DataScopeResolver dataScopeResolver) {
    return new DataScopeAspect(dataScopeResolver);
  }

  /**
   * 权限点启动上报 Runner（auth-service 自身排除）.
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(PermissionRegistrationPort.class)
  @ConditionalOnExpression("'${spring.application.name:unknown}' != 'auth-service'")
  public PermissionRegistrationRunner permissionRegistrationRunner(
    RequestMappingHandlerMapping handlerMapping,
    PermissionRegistrationPort registrationApi,
    Environment environment) {
    return new PermissionRegistrationRunner(handlerMapping, registrationApi, environment);
  }

  /**
   * 生产就绪校验：非 test/local/dev 环境必须存在权限客户端、执行器、切面与签名密钥.
   *
   * <p>解决“配置错误时切面静默不装配、@RequirePermission 全部失效”的 fail-open 问题.</p>
   */
  @Bean
  @ConditionalOnMissingBean
  public PermissionReadinessValidator permissionReadinessValidator(
    Environment environment,
    ObjectProvider<PermissionCheckPort> permissionCheckPort,
    ObjectProvider<PermissionExecutor> permissionExecutor,
    ObjectProvider<RequirePermissionAspect> requirePermissionAspect) {
    return new PermissionReadinessValidator(
      environment, permissionCheckPort, permissionExecutor, requirePermissionAspect);
  }

  /**
   * Phase D 失效接线：权限变更事件 → 本地缓存失效（spec §5 失效链消费端）.
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(PermissionCachePort.class)
  public PermissionCacheInvalidationListener permissionCacheInvalidationListener(
    PermissionCachePort cachePort) {
    return new PermissionCacheInvalidationListener(cachePort);
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
    @ConditionalOnBean({PermissionCacheInvalidationListener.class,
      org.springframework.data.redis.connection.RedisConnectionFactory.class})
    public PermissionInvalidationRedisSubscriber permissionInvalidationRedisSubscriber(
      org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory,
      PermissionCacheInvalidationListener invalidationListener,
      com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
      return new PermissionInvalidationRedisSubscriber(
        connectionFactory, invalidationListener, objectMapper);
    }
  }
}
