package com.pension.permission.infrastructure.authorization.config;

import com.example.shared.domain.annotation.DomainService;
import com.pension.permission.domain.authorization.enumeration.GrantOrigin;
import com.pension.permission.domain.authorization.service.DefaultGrantActivationPolicy;
import com.pension.permission.domain.authorization.spi.GrantActivationPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 授权域基础设施配置.
 *
 * <p>为 domain 层定义的 SPI 接口提供 Spring Bean 装配，避免 domain 层依赖 Spring。</p>
 *
 * <h3>职责</h3>
 * <ol>
 *   <li>扫描 {@code com.pension.permission} 包下标注 {@link DomainService} 的类，
 *       使其被 Spring 容器管理（AuthorizationEngine、GrantConfigurationFactory、
 *       DelegationFactory、EffectivePermissionService、IdentityResolutionService、
 *       PlanReachabilityService、RoleVisibilityResolver、RoleTemplateResolver 等）</li>
 *   <li>注册 {@link GrantActivationPolicy} SPI 实现</li>
 * </ol>
 *
 * <h3>可配置项</h3>
 * <ul>
 *   <li>{@code auth.grant.origins-requiring-approval}：需要审批的 GrantOrigin 列表，
 *       默认 {@code PLAN_DELEGATE,CUSTOMER_TO_AGENT}，可通过 application.yml 覆盖</li>
 * </ul>
 */
@Configuration
@ComponentScan(
  basePackages = {"com.pension.permission"},
  includeFilters = {@ComponentScan.Filter(type = FilterType.ANNOTATION, value = DomainService.class)})
public class AuthorizationInfrastructureConfig {

  @Bean
  public GrantActivationPolicy grantActivationPolicy(
    @Value("${auth.grant.origins-requiring-approval:PLAN_DELEGATE,CUSTOMER_TO_AGENT}") String originsCsv
  ) {
    Set<GrantOrigin> origins = Arrays.stream(originsCsv.split(","))
      .map(String::trim)
      .filter(s -> !s.isEmpty())
      .map(GrantOrigin::valueOf)
      .collect(Collectors.toSet());
    return new DefaultGrantActivationPolicy(origins);
  }
}
