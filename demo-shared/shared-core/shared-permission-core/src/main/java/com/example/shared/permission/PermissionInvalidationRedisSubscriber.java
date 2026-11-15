package com.example.shared.permission;

import com.example.shared.security.permission.PermissionChangedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.ChannelTopic;

/**
 * 权限失效事件 Redis 订阅者（P0-H2 收端）.
 *
 * <p>订阅 topic {@code event.PermissionChangedEvent}（auth 的
 * {@code PermissionInvalidationListener} 中继广播），反序列化后委托
 * {@link PermissionCacheInvalidationListener} 清除本地 Caffeine 权限缓存——
 * 打通"auth 权限变更 → 各业务服务缓存失效"的跨服务链路（此前只有同进程
 * Spring 事件，跨进程形同虚设，仅靠 TTL 300s 兜底）.
 *
 * <p>装配条件（PermissionAutoConfiguration）：
 * <ul>
 *   <li>classpath 存在 spring-data-redis（RedisConnectionFactory）</li>
 *   <li>{@code shared.permission.invalidation.redis.enabled}（默认 true）</li>
 * </ul>
 * 自管理 RedisMessageListenerContainer 生命周期，不依赖宿主服务的容器 bean.
 *
 * <p>失效幂等（清缓存无副作用），重复消费无害；Redis 不可用时订阅静默重连，
 * 期间由缓存 TTL 兜底，不放大故障面.
 *
 * @author shared-permission-starter
 */
@Slf4j
public class PermissionInvalidationRedisSubscriber implements InitializingBean, DisposableBean {

  private final RedisConnectionFactory connectionFactory;
  private final PermissionCacheInvalidationListener delegate;
  private final ObjectMapper objectMapper;
  private RedisMessageListenerContainer container;

  public PermissionInvalidationRedisSubscriber(RedisConnectionFactory connectionFactory,
                                               PermissionCacheInvalidationListener delegate,
                                               ObjectMapper objectMapper) {
    this.connectionFactory = connectionFactory;
    this.delegate = delegate;
    this.objectMapper = objectMapper;
  }

  @Override
  public void afterPropertiesSet() {
    container = new RedisMessageListenerContainer();
    container.setConnectionFactory(connectionFactory);
    container.addMessageListener((message, pattern) -> onMessage(message.getBody()),
      new ChannelTopic("event." + PermissionChangedEvent.class.getSimpleName()));
    container.afterPropertiesSet();
    container.start();
    log.info("[PermissionInvalidation] Redis 订阅已启动: topic=event.PermissionChangedEvent");
  }

  private void onMessage(byte[] body) {
    try {
      String json = new String(body, java.nio.charset.StandardCharsets.UTF_8);
      PermissionChangedEvent event = objectMapper.readValue(json, PermissionChangedEvent.class);
      delegate.onPermissionChanged(event);
    } catch (Exception e) {
      // fail-soft：单条消息解析失败不影响订阅；该次失效由 TTL 兜底
      log.warn("[PermissionInvalidation] 失效消息处理失败，回退 TTL 兜底: {}", e.getMessage());
    }
  }

  @Override
  public void destroy() {
    if (container != null) {
      container.stop();
      try {
        container.destroy();
      } catch (Exception e) {
        log.debug("[PermissionInvalidation] 订阅容器销毁异常（忽略）: {}", e.getMessage());
      }
    }
  }
}
