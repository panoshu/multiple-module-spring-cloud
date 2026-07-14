package com.example.shared.client.autoconfigure;

import com.example.shared.client.interceptor.EnhancedErrorInterceptor;
import com.github.lianjiatech.retrofit.spring.boot.interceptor.GlobalInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * ClientInterceptorConfig
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/2/5 22:34
 */
@Configuration
public class ClientInterceptorConfig {

  @Bean
  @Order(Integer.MIN_VALUE)
  public GlobalInterceptor interceptor() {
    return new EnhancedErrorInterceptor();
  }
}
