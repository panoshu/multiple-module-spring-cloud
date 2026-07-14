package com.example.shared.id.autoconfigure;

import com.example.shared.id.strategy.UlidStrategy;
import com.example.shared.id.strategy.UuidV7Strategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StatelessIdConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public UuidV7Strategy uuidV7Strategy() {
    return new UuidV7Strategy();
  }

  @Bean
  @ConditionalOnMissingBean
  public UlidStrategy ulidStrategy() {
    return new UlidStrategy();
  }
}
