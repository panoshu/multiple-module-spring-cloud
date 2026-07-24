package com.example.shared.id.autoconfigure;

import com.example.shared.lock.DistributedLock;
import com.example.shared.id.properties.IdProperties;
import com.example.shared.id.segment.allocator.SegmentAllocator;
import com.example.shared.id.segment.allocator.SegmentBufferAllocator;
import com.example.shared.id.segment.repository.JdbcSegmentRepository;
import com.example.shared.id.segment.repository.SegmentRepository;
import com.example.shared.id.segment.service.DefaultSegmentIdService;
import com.example.shared.id.segment.service.SegmentIdService;
import com.example.shared.id.strategy.SegmentStrategy;
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
