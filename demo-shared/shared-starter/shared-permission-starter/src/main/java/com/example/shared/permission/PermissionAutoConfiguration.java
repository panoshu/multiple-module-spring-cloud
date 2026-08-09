package com.example.shared.permission;

import com.example.auth.api.PermissionCheckApi;
import com.example.auth.api.annotation.RequirePermission;
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
 *   <li>{@code @ConditionalOnBean(PermissionCheckApi.class)} - 业务服务配置了 httpexchange 客户端</li>
 * </ul>
 *
 * <p>后端实时鉴权，不引入缓存层。权限变更天然立即生效。
 *
 * @author shared-permission-starter
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(RequirePermission.class)
@EnableConfigurationProperties(PermissionProperties.class)
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
  @ConditionalOnBean(PermissionCheckApi.class)
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
}
