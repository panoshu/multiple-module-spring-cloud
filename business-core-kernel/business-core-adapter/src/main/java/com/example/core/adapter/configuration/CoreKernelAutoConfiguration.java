package com.example.core.adapter.configuration;

import com.example.core.adapter.context.BusinessMetaContextAssembler;
import com.example.core.adapter.context.SessionContextResolver;
import com.example.core.application.business.guard.BusinessAccessGuard;
import com.example.core.application.business.guard.DefaultBusinessAccessGuard;
import com.example.core.application.engine.job.ExternalInteractionRecoveryJob;
import com.example.core.application.engine.service.BusinessConfigProcessDefinitionAdapter;
import com.example.core.application.engine.service.ExternalInteractionRecoveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.example.core.domain.engine.gateway.ProcessDefinitionGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * business-core-kernel 自动配置
 *
 * <p>业务服务引入 {@code business-core-adapter} 后,
 * 自动注册 kernel 组件:
 * <ul>
 *   <li>SessionContextResolver / BusinessMetaContextAssembler(会话基础设施)</li>
 *   <li>DefaultBusinessAccessGuard(默认权限守门人,业务服务可覆盖)</li>
 *   <li>默认 ProcessDefinitionGateway(过渡期适配存量步骤路由,业务服务可覆盖)</li>
 *   <li>ExternalInteractionRecoveryJob(外部协作恢复兜底任务,§19.2)</li>
 *   <li>5 类公共 API 的 Controller(Batch/Form/Application/Material/Progress)</li>
 *   <li>SupportedBusinessTypeValidator(业务类型校验)</li>
 * </ul>
 *
 * <p>功能权限校验由 shared-permission-starter 自动装配的 @RequirePermission AOP 切面承担,
 * 业务服务需在 application.yml 配置 {@code permission.service.base-url} 才会启用切面。
 *
 * <p>业务服务只需:
 * <ol>
 *   <li>提供 {@link com.example.core.api.registrar.BusinessTypeRegistrar} Bean 声明支持的业务类型</li>
 *   <li>(可选)提供自定义 {@link BusinessAccessGuard} 覆盖默认实现</li>
 *   <li>(可选)提供自定义 {@link ProcessDefinitionGateway} 覆盖过渡期默认适配器</li>
 * </ol>
 *
 * <p>后续新增自动注册组件流程:
 * <ol>
 *   <li>在对应模块标注 {@code @Component}/{@code @Service}/{@code @RestController}</li>
 *   <li>本类的 {@code @ComponentScan} 自动扫描 {@code com.example.core} 包</li>
 *   <li>需要兜底默认实现的,在本类添加 {@code @Bean} + {@code @ConditionalOnMissingBean} 方法</li>
 * </ol>
 *
 * @author panoshu
 */
@AutoConfiguration
@ComponentScan(basePackages = "com.example.core")
public class CoreKernelAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public SessionContextResolver sessionContextResolver(ObjectMapper objectMapper) {
    return new SessionContextResolver(objectMapper);
  }

  @Bean
  @ConditionalOnMissingBean
  public BusinessMetaContextAssembler businessMetaContextAssembler() {
    return new BusinessMetaContextAssembler();
  }

  @Bean
  @ConditionalOnMissingBean(BusinessAccessGuard.class)
  public BusinessAccessGuard defaultBusinessAccessGuard() {
    return new DefaultBusinessAccessGuard();
  }

  /**
   * 过渡期默认流程定义网关：把存量 BusinessConfigGateway 的步骤路由适配为版本化定义。
   * 业务服务提供自定义 {@link ProcessDefinitionGateway} 后本默认实现自动让位。
   */
  @Bean
  @ConditionalOnMissingBean(ProcessDefinitionGateway.class)
  public ProcessDefinitionGateway defaultProcessDefinitionGateway(BusinessConfigGateway configGateway) {
    return new BusinessConfigProcessDefinitionAdapter(configGateway);
  }

  /**
   * 外部协作恢复兜底任务（§19.2）。默认启用，可通过
   * {@code core.kernel.recovery.enabled=false} 关闭；调度依赖宿主的 {@code @EnableScheduling}。
   */
  @Bean
  @ConditionalOnProperty(name = "core.kernel.recovery.enabled", havingValue = "true", matchIfMissing = true)
  public ExternalInteractionRecoveryJob externalInteractionRecoveryJob(
      ExternalInteractionRecoveryService recoveryService) {
    return new ExternalInteractionRecoveryJob(recoveryService);
  }
}
