package com.example.shared.cache.autoconfigure;

import com.alicp.jetcache.CacheManager;
import com.example.shared.cache.core.ICacheTemplate;
import com.example.shared.cache.properties.SharedCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 共享缓存自动配置（JetCache 2.8 实现，2026-11-05 客户端统一治理重写）.
 *
 * <p><b>架构分层</b>：
 * <ul>
 *   <li><b>业务配置</b>：{@code shared.cache.rules.<name>}（声明式——缓存名/TTL/层级），
 *       由 {@link JetCacheSharedCacheTemplate} 翻译为 JetCache QuickConfig</li>
 *   <li><b>基础设施配置</b>：{@code jetcache.*}（连接/序列化/广播通道/统计），
 *       由 jetcache-starter 自动装配 {@link CacheManager}</li>
 * </ul>业务代码只依赖 {@link ICacheTemplate} 端口，两层配置各司其职.</p>
 *
 * <p>被替换的自研实现（LayeredCache/DynamicCacheManager/CacheSyncPolicy/
 * CaffeineCacheFactory/RedisCacheFactory/CacheSerializationConfiguration）：
 * 其两级查找回填、广播失效、防击穿能力由 JetCache 原生承接.</p>
 *
 * @author shared-cache-starter
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "com.alicp.jetcache.CacheManager")
@EnableConfigurationProperties(SharedCacheProperties.class)
public class SharedCacheAutoConfiguration {

  /**
   * 统一缓存端口实现：包装 JetCache CacheManager.
   *
   * <p>装配条件：jetcache 自动配置已建 CacheManager（{@code jetcache.remote.default.*}
   * 已配置）。无 Redis 宿主不装配（业务侧缓存 SPI 应有本地降级）.</p>
   */
  @Bean
  @ConditionalOnBean(CacheManager.class)
  @ConditionalOnMissingBean(ICacheTemplate.class)
  public ICacheTemplate sharedCacheTemplate(CacheManager cacheManager,
                                            SharedCacheProperties properties) {
    log.info("Initialized JetCacheSharedCacheTemplate (jetcache 2.8, Lettuce)");
    return new JetCacheSharedCacheTemplate(cacheManager, properties);
  }
}
