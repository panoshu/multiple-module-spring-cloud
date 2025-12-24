package com.example.outbound.server.configuration;

import com.example.outbound.server.domain.logistics.CarrierSelectionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DomainBeanConfiguration
 *
 * @author YourName
 * @since 2025/12/14 22:05
 */
@Configuration
public class DomainBeanConfiguration {

  /**
   * 将纯净的领域服务注册为 Spring Bean
   * 这样 Application Service 就可以注入它了
   */
  @Bean
  public CarrierSelectionPolicy carrierSelectionPolicy() {
    return new CarrierSelectionPolicy();
  }
}
