package com.example.shared.event.autoconfiguration;

import com.example.shared.event.contract.EventDispatcher;
import com.example.shared.event.contract.EventStore;
import com.example.shared.event.contract.IntegrationEventConverter;
import com.example.shared.event.bus.DefaultEventBus;
import com.example.shared.event.deliverer.EventDeliverer;
import com.example.shared.event.dispatcher.RedisEventDispatcher;
import com.example.shared.event.dispatcher.RocketMQEventDispatcher;
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
  // 恢复任务互斥已改 DB 认领（claimPendingLogs），无锁依赖
  @ConditionalOnMissingBean
  public EventRecoveryJob eventRecoveryJob(EventStore eventStore, EventDeliverer eventDeliverer, List<EventDispatcher> dispatchers) {
    return new EventRecoveryJob(eventStore, eventDeliverer, dispatchers);
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

  /**
   * Redis 事件通道（2026-11-08 批次1：收发闭环）.
   *
   * <p>发布端 {@link RedisEventDispatcher}（Envelope 防回声）+ 订阅端
   * {@link RedisEventSubscriber}（event.* → 本地 Spring 事件）——
   * 本服务发布的事件经 Redis 到达远端服务，远端服务的 @EventListener
   * 消费方即可收到跨服务事件；同时本服务的订阅端把远端事件转回本地.</p>
   */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.data.redis.core.RedisTemplate")
  @ConditionalOnProperty(prefix = "shared.event.redis", name = "enabled", havingValue = "true")
  static class RedisConfig {

    /**
     * 事件通道实例标识（防回声：订阅端跳过自己发布的消息）.
     */
    @Bean
    public String sharedEventInstanceId() {
      return java.util.UUID.randomUUID().toString();
    }

    /**
     * 事件专用 RedisTemplate：JSON 序列化携带类型信息（消费端按 @class 还原 DTO）.
     *
     * <p>不复用宿主的 RedisTemplate（序列化策略可能不同——如 sa-token 的），
     * 事件通道自持一套，互不干扰.</p>
     */
    @Bean("sharedEventRedisTemplate")
    @ConditionalOnMissingBean(name = "sharedEventRedisTemplate")
    public org.springframework.data.redis.core.RedisTemplate<String, Object> sharedEventRedisTemplate(
      org.springframework.data.redis.connection.RedisConnectionFactory factory) {
      org.springframework.data.redis.core.RedisTemplate<String, Object> template =
        new org.springframework.data.redis.core.RedisTemplate<>();
      template.setConnectionFactory(factory);
      // JavaTimeModule 必须注册：集成事件 DTO 普遍携带 LocalDateTime（E2E 实证：
      // 未注册时序列化抛 "Java 8 date/time type not supported"，投递失败）
      com.fasterxml.jackson.databind.ObjectMapper eventObjectMapper =
        new com.fasterxml.jackson.databind.ObjectMapper();
      eventObjectMapper.findAndRegisterModules();
      eventObjectMapper.activateDefaultTyping(
        com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator.instance,
        com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.NON_FINAL,
        com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY);
      org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer serializer =
        new org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer(eventObjectMapper);
      template.setKeySerializer(new org.springframework.data.redis.serializer.StringRedisSerializer());
      template.setValueSerializer(serializer);
      template.setHashKeySerializer(new org.springframework.data.redis.serializer.StringRedisSerializer());
      template.setHashValueSerializer(serializer);
      template.afterPropertiesSet();
      return template;
    }

    @Bean
    public EventDispatcher redisEventDispatcher(
      @org.springframework.beans.factory.annotation.Qualifier("sharedEventRedisTemplate")
      org.springframework.data.redis.core.RedisTemplate<String, Object> template,
      String sharedEventInstanceId) {
      return new RedisEventDispatcher(template, sharedEventInstanceId);
    }

    @Bean
    public com.example.shared.event.dispatcher.RedisEventSubscriber redisEventSubscriber(
      @org.springframework.beans.factory.annotation.Qualifier("sharedEventRedisTemplate")
      org.springframework.data.redis.core.RedisTemplate<String, Object> template,
      ApplicationEventPublisher publisher,
      String sharedEventInstanceId) {
      return new com.example.shared.event.dispatcher.RedisEventSubscriber(template, publisher, sharedEventInstanceId);
    }

    @Bean
    public org.springframework.data.redis.listener.RedisMessageListenerContainer sharedEventListenerContainer(
      org.springframework.data.redis.connection.RedisConnectionFactory factory,
      com.example.shared.event.dispatcher.RedisEventSubscriber subscriber) {
      org.springframework.data.redis.listener.RedisMessageListenerContainer container =
        new org.springframework.data.redis.listener.RedisMessageListenerContainer();
      container.setConnectionFactory(factory);
      // PatternTopic 模式订阅：event.FileParsedEvent / event.OutboundReadyEvent 等全部事件通道
      container.addMessageListener(subscriber,
        new org.springframework.data.redis.listener.PatternTopic("event.*"));
      return container;
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
