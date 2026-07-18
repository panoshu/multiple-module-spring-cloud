package com.example.shared.event.autoconfiguration;

import com.example.shared.cache.lock.DistributedLock;
import com.example.shared.domain.event.EventDispatcher;
import com.example.shared.domain.event.EventStore;
import com.example.shared.event.bus.EventBus;
import com.example.shared.event.deliverer.EventDeliverer;
import com.example.shared.event.dispatcher.RocketMQEventDispatcher;
import com.example.shared.event.dispatcher.RedisEventDispatcher;
import com.example.shared.event.dispatcher.SpringEventDispatcher;
import com.example.shared.event.jackson.DddJacksonModule;
import com.example.shared.event.job.EventRecoveryJob;
import com.example.shared.event.store.JdbcEventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.util.List;

@AutoConfiguration
public class EventAutoConfiguration {

  // 1. 注册 Jackson Module (使得所有 ObjectMapper 都能正确序列化 ID)
  @Bean
  public DddJacksonModule dddJacksonModule() {
    return new DddJacksonModule();
  }

  // 2. EventStore (持久化)
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass({DataSource.class, JdbcClient.class})
  public EventStore eventStore(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    // 如果当前环境有自定义ObjectMapper，Spring会自动注入
    // 确保这个ObjectMapper加载了上面的 Module
    return new JdbcEventStore(jdbcClient, objectMapper);
  }

  // 3. 本地分发器 (总是启用)
  @Bean
  @ConditionalOnMissingBean
  public SpringEventDispatcher springEventDispatcher(ApplicationEventPublisher publisher) {
    return new SpringEventDispatcher(publisher);
  }

  @Bean
  @ConditionalOnMissingBean
  public EventDeliverer eventDeliverer(EventStore eventStore) {
    return new EventDeliverer(eventStore);
  }

  @Bean
  @ConditionalOnBean(DistributedLock.class)
  @ConditionalOnMissingBean
  public EventRecoveryJob eventRecoveryJob(
    EventStore eventStore,
    EventDeliverer eventDeliverer,
    List<EventDispatcher> dispatchers,
    DistributedLock distributedLock
  ) {
    return new EventRecoveryJob(eventStore, eventDeliverer, dispatchers, distributedLock);
  }

  // 6. 核心 EventBus
  @Bean
  @ConditionalOnMissingBean
  public com.example.shared.domain.event.EventBus eventBus(
    List<EventDispatcher> dispatchers,
    EventStore eventStore,
    EventDeliverer eventDeliverer
  ) {
    return new EventBus(dispatchers, eventStore, eventDeliverer);
  }

  // 4. Redis 分发器 (按需)
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
  @ConditionalOnProperty(prefix = "shared.event.redis", name = "enabled", havingValue = "true")
  static class RedisConfig {
    @Bean
    public EventDispatcher redisEventDispatcher(org.springframework.data.redis.core.RedisTemplate<String, Object> template) {
      return new RedisEventDispatcher(template);
    }
  }

  // 5. RocketMQ 分发器 (按需)
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
  @ConditionalOnProperty(prefix = "shared.event.rocketmq", name = "enabled", havingValue = "true")
  static class RocketMQConfig {

    @Bean
    public EventDispatcher rocketMQEventDispatcher(org.apache.rocketmq.spring.core.RocketMQTemplate template) {
      return new RocketMQEventDispatcher(template);
    }
  }
}
