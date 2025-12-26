package com.example.shared.core.autoconfigure;

import com.example.shared.core.infrastructure.ExternalCallTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AutoConfig
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/25 23:17
 */
@Configuration(proxyBeanMethods = false)
public class InfrastructureConfiguration {

  @Bean
  @ConditionalOnMissingBean(name = "externalCallTemplate")
  public ExternalCallTemplate externalCallTemplate() {
    return new ExternalCallTemplate();
  }
}
