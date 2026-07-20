package com.example.file.infrastructure.storage;

import com.example.file.domain.gateway.FileTokenStore;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * RedisFileTokenStore 自动装配。
 * <p>
 * 激活条件：
 * <ul>
 *   <li>classpath 存在 RedissonClient（由 file-infrastructure 强依赖传递保证）</li>
 *   <li>file.token.secret-key 已配置（与 KonaAutoConfiguration 一致，确保 FileTokenProperties @NotBlank 校验通过）</li>
 *   <li>容器中存在 RedissonClient Bean（Redis 已配置）</li>
 *   <li>容器中尚无自定义 FileTokenStore Bean</li>
 * </ul>
 *
 * <p>采用独立自动装配类（而非修改 KonaAutoConfiguration）以保持 Task 9 实现不变，
 * 同时与 StorageAutoConfiguration / KonaAutoConfiguration 模式对齐。
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.redisson.api.RedissonClient")
@ConditionalOnProperty(prefix = "file.token", name = "secret-key")
@EnableConfigurationProperties(FileTokenProperties.class)
public class RedisFileTokenStoreAutoConfiguration {

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnMissingBean(FileTokenStore.class)
    public FileTokenStore redisFileTokenStore(RedissonClient redissonClient, FileTokenProperties properties) {
        return new RedisFileTokenStore(redissonClient, properties);
    }
}
