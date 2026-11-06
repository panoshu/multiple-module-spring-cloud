package com.example.file.infrastructure;

import com.example.file.domain.gateway.FileTokenStore;
import com.example.file.infrastructure.storage.FileTokenProperties;
import com.example.file.infrastructure.storage.KonaAutoConfiguration;
import com.example.file.infrastructure.storage.KonaFileTokenGateway;
import com.example.file.infrastructure.storage.RedisFileTokenStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FileAccessIntegrationTest 专用 Spring 启动配置。
 *
 * <p><b>关键设计决策：包路径选择</b>
 * 本配置类放在 {@code com.example.file.infrastructure} 包（而非 {@code storage} 子包），
 * 原因是 {@link com.example.file.infrastructure.storage.StorageTestConfiguration}
 * 使用了 {@code @ComponentScan(basePackages = "com.example.file.infrastructure.storage")}，
 * 会扫描 storage 包及子包下所有 @Configuration 类。
 * 若本类放在 storage 包下，会被 StorageIntegrationTest 误加载，导致 FileTokenProperties
 * 在 StorageIntegrationTest 的 @TestPropertySource（无 file.token.secret-key）下校验失败。
 *
 * <p>设计要点：
 * <ul>
 *   <li>使用 @SpringBootConfiguration（非 @SpringBootApplication）避免级联触发
 *       shared-id-starter / shared-cache-starter / DataSourceAutoConfiguration
 *       等无关自动配置</li>
 *   <li>显式 @Import {@link KonaAutoConfiguration} —— 该自动配置类注册
 *       {@link KonaFileTokenGateway}（真实 SM4 加解密实现），
 *       且其内部条件 {@code @ConditionalOnMissingBean(FileTokenGateway.class)}
 *       在测试上下文中自然满足</li>
 *   <li>手动注册 {@link RedisFileTokenStore} Bean —— 因
 *       {@link com.example.file.infrastructure.storage.RedisFileTokenStoreAutoConfiguration}
 *       的 {@code @ConditionalOnBean(StringRedisTemplate.class)} 在测试上下文中无法识别
 *       同级 @Bean 定义的 StringRedisTemplate（条件求值时机问题），
 *       故直接手动 new RedisFileTokenStore(mockStringRedisTemplate, properties)，
 *       仍然测试 RedisFileTokenStore 的真实 SETNX 一次性语义</li>
 *   <li>Mock {@link StringRedisTemplate} 模拟 Redis SETNX 语义
 *       （2026-11-05 客户端统一治理：原 Redisson RBucket mock 改为 Lettuce 通道）：
 *       <ul>
 *         <li>每个 key 对应独立的 {@link AtomicBoolean}</li>
 *         <li>setIfAbsent 首次返回 true，后续返回 false（真实 SETNX 语义）</li>
 *         <li>hasKey 返回 key 是否已被设置</li>
 *       </ul>
 *       不依赖真实 Redis 实例</li>
 *   <li>提供 {@link ObjectMapper}（注册 JavaTimeModule 支持 LocalDateTime），
 *       因未启用 @SpringBootApplication 自动配置，需手动提供</li>
 *   <li>不加载 DataSource / MyBatis-Flex —— 焦点是 Token 加解密 + 一次性语义</li>
 * </ul>
 */
@SpringBootConfiguration
@Import(KonaAutoConfiguration.class)
@EnableConfigurationProperties(FileTokenProperties.class)
public class FileAccessIntegrationTestConfiguration {

  /**
   * Jackson ObjectMapper，注册 JavaTimeModule 支持 {@link java.time.LocalDateTime} 序列化。
   *
   * <p>{@link KonaFileTokenGateway} 加密时将 {@code FileTokenPayload} 序列化为 JSON，
   * 解密时反序列化回对象，因此需要 ObjectMapper 支持 Java 时间类型。
   */
  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    return mapper;
  }

  /**
   * Mock StringRedisTemplate，模拟 Redis SETNX + TTL 语义.
   *
   * <p>每个 key 维护独立的 {@link AtomicBoolean}：
   * <ul>
   *   <li>首次 {@code setIfAbsent(key, value, ttl)} 通过 CAS (false→true) 返回 true</li>
   *   <li>后续调用 CAS 失败返回 false</li>
   *   <li>{@code hasKey(key)} 返回 {@code AtomicBoolean.get()}</li>
   * </ul>
   *
   * <p>这样 {@link RedisFileTokenStore} 的真实 SETNX 一次性语义被完整验证，
   * 无需真实 Redis 实例。
   */
  @Bean
  @SuppressWarnings("unchecked")
  public StringRedisTemplate stringRedisTemplate() {
    StringRedisTemplate template = mock(StringRedisTemplate.class);
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    when(template.opsForValue()).thenReturn(valueOps);

    Map<String, AtomicBoolean> used = new ConcurrentHashMap<>();

    when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
      .thenAnswer(inv -> used
        .computeIfAbsent(inv.getArgument(0), k -> new AtomicBoolean(false))
        .compareAndSet(false, true));

    when(template.hasKey(anyString()))
      .thenAnswer(inv -> used
        .getOrDefault(inv.getArgument(0), new AtomicBoolean(false))
        .get());

    return template;
  }

  /**
   * 手动注册 {@link RedisFileTokenStore} Bean.
   *
   * <p>不通过 {@link com.example.file.infrastructure.storage.RedisFileTokenStoreAutoConfiguration}
   * 注册的原因：该自动配置类的 {@code @ConditionalOnBean(StringRedisTemplate.class)} 在
   * 测试上下文中无法识别本类同级 @Bean 定义的 StringRedisTemplate
   * （auto-config 条件求值时机早于 regular @Bean 注册），导致 bean 被跳过。
   * 直接手动 new 可绕过此限制，仍测试 RedisFileTokenStore 的真实实现逻辑。
   */
  @Bean
  public FileTokenStore fileTokenStore(StringRedisTemplate redisTemplate, FileTokenProperties properties) {
    return new RedisFileTokenStore(redisTemplate, properties);
  }
}
