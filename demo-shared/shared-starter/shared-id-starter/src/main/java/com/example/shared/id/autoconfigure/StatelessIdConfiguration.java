package com.example.shared.id.autoconfigure;

import com.example.shared.id.strategy.SnowflakeStrategy;
import com.example.shared.id.strategy.UlidStrategy;
import com.example.shared.id.strategy.UuidV7Strategy;
import org.springframework.beans.factory.annotation.Value;
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

  /**
   * 雪花策略（补齐 IdType.SNOWFLAKE 声明的实现缺口）.
   * worker-id：0-1023，多实例部署必须唯一（shared.id.snowflake.worker-id 配置）.
   */
  @Bean
  @ConditionalOnMissingBean
  public SnowflakeStrategy snowflakeStrategy(
    @Value("${shared.id.snowflake.worker-id:0}") long workerId) {
    return new SnowflakeStrategy(workerId);
  }
}
