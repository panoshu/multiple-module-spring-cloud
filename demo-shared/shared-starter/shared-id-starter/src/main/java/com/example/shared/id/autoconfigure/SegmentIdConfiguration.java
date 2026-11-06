package com.example.shared.id.autoconfigure;

import com.example.shared.id.properties.IdProperties;
import com.example.shared.id.segment.allocator.SegmentAllocator;
import com.example.shared.id.segment.allocator.SegmentBufferAllocator;
import com.example.shared.id.segment.repository.JdbcSegmentRepository;
import com.example.shared.id.segment.repository.SegmentRepository;
import com.example.shared.id.segment.service.DefaultSegmentIdService;
import com.example.shared.id.segment.service.SegmentIdService;
import com.example.shared.id.strategy.SegmentStrategy;
import com.example.shared.lock.DistributedLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({DataSource.class, JdbcClient.class})
@EnableConfigurationProperties(IdProperties.class)
public class SegmentIdConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public JdbcClient jdbcClient(DataSource dataSource) {
    return JdbcClient.create(dataSource);
  }

  @Bean
  @ConditionalOnMissingBean
  public SegmentRepository segmentRepository(JdbcClient jdbcClient) {
    return new JdbcSegmentRepository(jdbcClient);
  }

  /**
   * 号段本地缓存兜底（2026-11-05 缓存重构：DynamicCacheManager 移除后供给 Spring CacheManager）.
   *
   * <p>号段双缓冲本就是<b>进程内缓存</b>语义（号段分配互斥由分布式锁保证，
   * 本地缓存只是避免频繁取号）——id-starter 自带 Caffeine 兜底，
   * 解除对 cache-starter 的隐性依赖；宿主自定义 CacheManager 时本兜底让位.</p>
   */
  @Bean
  @ConditionalOnMissingBean(CacheManager.class)
  public org.springframework.cache.caffeine.CaffeineCacheManager segmentCacheManager() {
    org.springframework.cache.caffeine.CaffeineCacheManager manager =
      new org.springframework.cache.caffeine.CaffeineCacheManager();
    manager.setCacheNames(java.util.Set.of("segment_buffer"));
    return manager;
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean({SegmentRepository.class, DistributedLock.class, CacheManager.class})
  public SegmentAllocator segmentAllocator(SegmentRepository repo, DistributedLock lock, CacheManager cacheManager) {
    return new SegmentBufferAllocator(repo, lock, cacheManager);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(SegmentAllocator.class)
  public SegmentIdService segmentIdService(SegmentAllocator allocator, IdProperties properties) {
    return new DefaultSegmentIdService(allocator, properties);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(SegmentIdService.class)
  public SegmentStrategy segmentStrategy(SegmentIdService service) {
    return new SegmentStrategy(service);
  }
}
