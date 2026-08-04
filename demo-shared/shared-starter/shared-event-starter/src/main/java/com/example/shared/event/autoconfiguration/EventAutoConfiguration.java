package com.example.shared.event.autoconfiguration;

import com.example.shared.domain.event.EventDispatcher;
import com.example.shared.domain.event.EventStore;
import com.example.shared.domain.event.IntegrationEventConverter;
import com.example.shared.event.bus.DefaultEventBus;
import com.example.shared.event.deliverer.EventDeliverer;
import com.example.shared.event.dispatcher.RedisEventDispatcher;
import com.example.shared.event.dispatcher.RocketMQEventDispatcher;
import com.example.shared.event.dispatcher.SpringEventDispatcher;
import com.example.shared.event.jackson.DddJacksonModule;
import com.example.shared.event.job.EventRecoveryJob;
import com.example.shared.event.store.JdbcEventStore;
import com.example.shared.lock.DistributedLock;
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
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.util.List;

/**
 * 事件模块自动配置。
 * <p>
 * 标注 {@link EnableScheduling} 以激活 {@code EventRecoveryJob} 的 {@code @Scheduled} 补偿任务，
 * 确保未投递的领域事件能被定时重试。
 *
 * @author panoshu
 */
@AutoConfiguration
@EnableScheduling
public class EventAutoConfiguration {

  @Bean
  public DddJacksonModule dddJacksonModule() {
    return new DddJacksonModule();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnClass({DataSource.class, JdbcClient.class})
  public EventStore eventStore(JdbcClient jdbcClient, ObjectMapper objectMapper) {
    return new JdbcEventStore(jdbcClient, objectMapper);
  }

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
    DistributedLock distributedLock) {
    return new EventRecoveryJob(eventStore, eventDeliverer, dispatchers, distributedLock);
  }

  @Bean
  @ConditionalOnMissingBean
  public com.example.shared.domain.event.EventBus eventBus(
    List<EventDispatcher> dispatchers,
    EventStore eventStore,
    EventDeliverer eventDeliverer,
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    List<IntegrationEventConverter<?>> converters) {
    List<IntegrationEventConverter<?>> actualConverters =
      converters != null ? converters : List.of();
    return new DefaultEventBus(dispatchers, eventStore, eventDeliverer, actualConverters);
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
  @ConditionalOnProperty(prefix = "shared.event.redis", name = "enabled", havingValue = "true")
  static class RedisConfig {
    @Bean
    public EventDispatcher redisEventDispatcher(
      org.springframework.data.redis.core.RedisTemplate<String, Object> template) {
      return new RedisEventDispatcher(template);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.apache.rocketmq.spring.core.RocketMQTemplate")
  @ConditionalOnProperty(prefix = "shared.event.rocketmq", name = "enabled", havingValue = "true")
  static class RocketMQConfig {
    @Bean
    public EventDispatcher rocketMQEventDispatcher(
      org.apache.rocketmq.spring.core.RocketMQTemplate template) {
      return new RocketMQEventDispatcher(template);
    }
  }
}
