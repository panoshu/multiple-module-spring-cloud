package com.example.annuity;

import com.example.core.domain.engine.service.step.MaterialRuleEngine;
import com.example.core.domain.engine.service.registry.BusinessFactExtractorRegistry;
import com.example.core.domain.engine.service.registry.ExtensionActionRegistry;
import com.example.core.domain.engine.service.registry.StepActionHandlerRegistry;
import com.example.core.domain.engine.spi.BusinessFactExtractor;
import com.example.core.domain.engine.spi.StepActionHandler;
import com.example.core.domain.engine.spi.StepExtensionAction;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Kernel 领域服务测试配置
 * <p>
 * <b>【存在原因】</b>kernel 内部存在两个 {@code @DomainService} 注解：
 * <ul>
 *   <li>{@code com.example.shared.domain.annotation.DomainService} - shared-domain 模块定义</li>
 *   <li>{@code com.example.core.domain.engine.annotation.DomainService} - business-core-domain 模块定义</li>
 * </ul>
 * 但 {@code DomainServiceConfiguration} 与 {@code AnnuityDomainServiceConfiguration} 的
 * {@code @ComponentScan.Filter} 仅过滤 shared 版本，导致 kernel 的
 * {@link StepActionHandlerRegistry}、{@link BusinessFactExtractorRegistry}、
 * {@link ExtensionActionRegistry}、{@link MaterialRuleEngine}（均使用 core 版本注解）
 * 无法被自动扫描注册。
 * <p>
 * 本配置类显式注册这 4 个 Bean，让 Spring 上下文能完成依赖注入。
 * 待 kernel 修复注解一致性问题后可移除本配置。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@TestConfiguration
public class KernelDomainServiceTestConfiguration {

  @Bean
  public StepActionHandlerRegistry stepActionHandlerRegistry(List<StepActionHandler> handlers) {
    return new StepActionHandlerRegistry(handlers);
  }

  @Bean
  public BusinessFactExtractorRegistry businessFactExtractorRegistry(
      List<BusinessFactExtractor> extractors) {
    return new BusinessFactExtractorRegistry(extractors);
  }

  @Bean
  public ExtensionActionRegistry extensionActionRegistry(List<StepExtensionAction> actions) {
    return new ExtensionActionRegistry(actions);
  }

  @Bean
  public MaterialRuleEngine materialRuleEngine() {
    return new MaterialRuleEngine();
  }
}
