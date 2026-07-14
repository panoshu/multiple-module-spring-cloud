package com.example.shared.cache.autoconfigure;

import com.example.shared.cache.core.*;
import com.example.shared.cache.lock.DistributedLockFactory;
import com.example.shared.cache.properties.SharedCacheProperties;
import com.example.shared.cache.service.SharedCacheTemplate;
import com.example.shared.cache.support.CaffeineCacheFactory;
import com.example.shared.cache.support.RedisCacheFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.UUID;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SharedCacheProperties.class)
@AutoConfigureAfter(RedisAutoConfiguration.class)
@Import({CacheSerializationConfiguration.class, DistributedLockConfiguration.class})
public class SharedCacheAutoConfiguration {

  // ==========================================
  // 1. L1 工厂 (默认 Caffeine)
  // ==========================================
  @Bean
  @ConditionalOnClass(name = "com.github.benmanes.caffeine.cache.Caffeine")
  @ConditionalOnMissingBean(name = "l1CacheFactory")
  public ICacheFactory l1CacheFactory() {
    return new CaffeineCacheFactory();
  }

  // ==========================================
  // 3. 核心管理器组装
  // ==========================================
  @Bean
  @ConditionalOnMissingBean(CacheManager.class)
  public DynamicCacheManager cacheManager(
    SharedCacheProperties properties,
    // 注入 L1 工厂 (必选)
    @Qualifier("l1CacheFactory") ICacheFactory l1Factory,
    // 注入 L2 工厂 (可选，如果 RedisSupport 未加载则为 null)
    @Qualifier("l2CacheFactory") ObjectProvider<ICacheFactory> l2FactoryProvider,
    // 注入 同步策略 (可选)
    ObjectProvider<CacheSyncPolicy> syncPolicyProvider,
    // 注入 分布式锁工厂 (必选，DistributedLockConfiguration 保证了兜底)
    DistributedLockFactory lockFactory
  ) {
    ICacheFactory l2Factory = l2FactoryProvider.getIfAvailable();
    CacheSyncPolicy syncPolicy = syncPolicyProvider.getIfAvailable(CacheSyncPolicy.NoOp::new);

    return new DynamicCacheManager(properties, l1Factory, l2Factory, syncPolicy, lockFactory);
  }

  /**
   * 【新增】注册统一缓存服务门面
   * 业务代码通过注入 ICacheTemplate 来使用缓存，而不是直接注入 CacheManager
   */
  @Bean
  @ConditionalOnMissingBean(ICacheTemplate.class)
  public SharedCacheTemplate defaultCacheService(CacheManager cacheManager) {
    return new SharedCacheTemplate(cacheManager);
  }

  // ==========================================
  // 2. Redis 支持 (隔离配置)
  // 只有在引入了 Redis 依赖 且 配置了连接工厂时才加载
  // ==========================================
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass({RedisTemplate.class, RedisCacheWriter.class})
  @ConditionalOnBean(RedisConnectionFactory.class)
  static class RedisSupportConfiguration {

    /**
     * 注册 Redis L2 工厂
     */
    @Bean
    public ICacheFactory l2CacheFactory(RedisConnectionFactory connectionFactory,
                                        @Qualifier("cacheValueSerializer") RedisSerializer<Object> valueSerializer) {
      RedisCacheWriter writer = RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory);
      return new RedisCacheFactory(writer, valueSerializer);
    }

    /**
     * 注册 Redis 同步策略
     */
    @Bean
    public CacheSyncPolicy cacheSyncPolicy(RedisTemplate<String, Object> redisTemplate,
                                           SharedCacheProperties properties) {
      String instanceId = UUID.randomUUID().toString();
      return new RedisCacheSyncPolicy(redisTemplate, properties.topic(), instanceId);
    }

    /**
     * 注册专用 RedisTemplate
     */
    @Bean("cacheRedisTemplate")
    public RedisTemplate<String, Object> cacheRedisTemplate(RedisConnectionFactory connectionFactory,
                                                            @Qualifier("cacheValueSerializer") RedisSerializer<Object> valueSerializer) {
      RedisTemplate<String, Object> template = new RedisTemplate<>();
      template.setConnectionFactory(connectionFactory);
      template.setKeySerializer(new StringRedisSerializer());
      template.setValueSerializer(valueSerializer);
      template.afterPropertiesSet();
      return template;
    }

    /**
     * 注册监听器
     */
    @Bean
    public RedisMessageListenerContainer cacheMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                       CacheManager cacheManager, // 注入顶层接口
                                                                       SharedCacheProperties properties,
                                                                       @Qualifier("cacheValueSerializer") RedisSerializer<Object> serializer) {
      RedisMessageListenerContainer container = new RedisMessageListenerContainer();
      container.setConnectionFactory(connectionFactory);

      // 匿名内部类实现监听逻辑
      container.addMessageListener((message, pattern) -> {
        try {
          Object body = serializer.deserialize(message.getBody());
          if (body instanceof CacheEvictMessage evictMsg) {
            // 简单粗暴但有效：直接调用 CacheManager 的 getCache 获取实例
            // 无论它是 LayeredCache 还是 CaffeineCache，evict 都是安全的
            // 注意：为了防止回环广播，最好检查 instanceId。但这里为简化逻辑，清空本地无害。
            org.springframework.cache.Cache cache = cacheManager.getCache(evictMsg.cacheName());
            if (cache != null) {
              if (evictMsg.key() == null) {
                cache.clear();
              } else {
                cache.evict(evictMsg.key());
              }
            }
          }
        } catch (Exception e) {
          log.error("Failed to handle cache broadcast", e);
        }
      }, new ChannelTopic(properties.topic()));

      return container;
    }
  }

  // RedisSyncPolicy 实现 (静态内部类以避免外部类加载时的依赖问题)
  static class RedisCacheSyncPolicy implements CacheSyncPolicy {
    private final RedisTemplate<String, Object> redisTemplate;
    private final String topic;
    private final String instanceId;

    public RedisCacheSyncPolicy(RedisTemplate<String, Object> redisTemplate, String topic, String instanceId) {
      this.redisTemplate = redisTemplate;
      this.topic = topic;
      this.instanceId = instanceId;
    }

    @Override
    public void publishEvict(String cacheName, Object key) {
      redisTemplate.convertAndSend(topic, new CacheEvictMessage(cacheName, key, instanceId));
    }

    @Override
    public void publishClear(String cacheName) {
      redisTemplate.convertAndSend(topic, new CacheEvictMessage(cacheName, null, instanceId));
    }
  }
}
