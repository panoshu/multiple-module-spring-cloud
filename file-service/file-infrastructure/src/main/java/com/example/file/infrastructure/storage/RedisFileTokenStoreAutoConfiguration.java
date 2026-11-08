package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileTokenStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * RedisFileTokenStore 自动装配。
 * <p>
 * 激活条件：
 * <ul>
 *   <li>classpath 存在 StringRedisTemplate（spring-boot-starter-data-redis 传递保证）</li>
 *   <li>file.token.secret-key 已配置（与 KonaAutoConfiguration 一致，确保 FileTokenProperties @NotBlank 校验通过）</li>
 *   <li>容器中存在 StringRedisTemplate Bean（Redis 已配置）</li>
 *   <li>容器中尚无自定义 FileTokenStore Bean</li>
 * </ul>
 *
 * <p>2026-11-05 客户端统一治理：Redisson 替换为 Lettuce（StringRedisTemplate），
 * SETNX 语义等价。
 *
 * <p>使用 {@code @AutoConfigureAfter} 确保在 Redis 自动配置之后加载，
 * 避免 {@code @ConditionalOnBean(StringRedisTemplate.class)} 因加载顺序问题而评估为 false。
 */
@AutoConfiguration
@AutoConfigureAfter(RedisAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
@ConditionalOnProperty(prefix = "file.token", name = "secret-key")
@EnableConfigurationProperties(FileTokenProperties.class)
public class RedisFileTokenStoreAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(FileTokenStore.class)
  public FileTokenStore redisFileTokenStore(StringRedisTemplate redisTemplate, FileTokenProperties properties) {
    return new RedisFileTokenStore(redisTemplate, properties);
  }
}
