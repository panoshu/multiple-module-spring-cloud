package com.example.annuity.application.configuration;

import com.example.core.api.registrar.BusinessTypeRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 年金服务 Kernel 集成配置
 *
 * <p>提供 kernel 基础设施所需的 Bean 注册:
 * <ul>
 *   <li>{@link BusinessTypeRegistrar} — 声明本服务支持的业务类型，
 *       由 {@code SupportedBusinessTypeValidator} 在 Controller 入口校验</li>
 * </ul>
 *
 * @author panoshu
 */
@Configuration
public class AnnuityKernelConfiguration {

  @Bean
  public BusinessTypeRegistrar annuityTypeRegistrar() {
    return BusinessTypeRegistrar.of("ANNUITY_OPEN", "ANNUITY_CHANGE");
  }
}
