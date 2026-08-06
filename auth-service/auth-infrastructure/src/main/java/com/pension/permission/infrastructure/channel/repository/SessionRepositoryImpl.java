package com.pension.permission.infrastructure.channel.repository;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.identifier.id.UserNo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.enumeration.SessionStatus;
import com.pension.permission.domain.channel.repository.SessionRepository;
import com.pension.permission.infrastructure.channel.converter.SessionConverter;
import com.pension.permission.infrastructure.channel.entity.SessionDO;
import com.pension.permission.types.SessionId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 渠道会话仓储实现（基于 Redis）.
 *
 * <p>本实现将 {@link Session} 聚合根序列化为 JSON 存储 to Redis，复用 Sa-Token 引入的
 * {@code sa-token-redis-template} 基础设施（{@link StringRedisTemplate}）。</p>
 *
 * <h3>Redis Key 设计</h3>
 * <ul>
 *   <li>{@code auth:session:id:{sessionId}} —— 主键索引，存储 Session JSON，TTL = expiresAt - now</li>
 *   <li>{@code auth:session:account:{primaryAccountId}:channel:{channel}} —— 主账号+渠道索引（仅活跃会话），
 *       值为 sessionId，TTL = expiresAt - now</li>
 * </ul>
 *
 * <h3>会话 ID 与 Token 的关系</h3>
 * <p>{@code Session.id} 直接等于 Sa-Token 签发的 tokenValue，二者合一，
 * 使得"给定 token 找会话"无需额外映射表，{@code load(new SessionId(token))} 即可。</p>
 *
 * <p>领域事件不在 Repository 发布，由 {@code ApplicationService} 在编排时统一发布。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SessionRepositoryImpl implements SessionRepository {

  /**
   * Redis Key 前缀.
   */
  private static final String KEY_PREFIX_ID = "auth:session:id:";
  private static final String KEY_PREFIX_ACCOUNT = "auth:session:account:";

  private final StringRedisTemplate redisTemplate;
  private final SessionConverter converter;

  /**
   * Jackson ObjectMapper：处理 LocalDateTime + SessionDO.
   */
  private final ObjectMapper objectMapper = new ObjectMapper()
    .registerModule(new JavaTimeModule())
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  @Override
  public Optional<Session> load(SessionId id) {
    if (id == null) {
      return Optional.empty();
    }
    String json = redisTemplate.opsForValue().get(keyOfId(id));
    return Optional.ofNullable(deserialize(json));
  }

  @Override
  public void save(Session session) {
    if (session == null) {
      throw new IllegalArgumentException("Session 不能为空");
    }

    SessionDO doObj = converter.toDO(session);
    String json = serialize(doObj);
    Duration ttl = remainingTtl(session);

    // 1. 主键索引
    redisTemplate.opsForValue().set(keyOfId(session.id()), json, ttl);

    // 2. 主账号+渠道索引（仅活跃会话才建索引，便于按账号反查）
    if (session.status() == SessionStatus.ACTIVE) {
      redisTemplate.opsForValue().set(
        keyOfAccount(session.primaryAccountId(), session.channel()),
        session.id().value(),
        ttl);
    } else {
      // 状态变化为非 ACTIVE 时清理索引，避免索引指向已关闭/已过期的会话
      redisTemplate.delete(keyOfAccount(session.primaryAccountId(), session.channel()));
    }

    log.debug("保存 Session: sessionId={}, status={}, ttl={}s",
      session.id(), session.status(), ttl.getSeconds());
  }

  @Override
  public void delete(Session aggregateRoot) {
    if (aggregateRoot == null) {
      return;
    }
    deleteById(aggregateRoot.id(), aggregateRoot.primaryAccountId(), aggregateRoot.channel());
    log.debug("删除 Session: sessionId={}", aggregateRoot.id());
  }

  @Override
  public void deleteById(SessionId id) {
    if (id == null) {
      return;
    }
    // 删除时需要先 load 以获得 primaryAccountId 和 channel 用于清理索引
    load(id).ifPresent(session -> deleteById(id, session.primaryAccountId(), session.channel()));
    log.debug("根据 ID 删除 Session: sessionId={}", id);
  }

  @Override
  public List<Session> loadAll() {
    // Redis 不支持高效的全量扫描，本方法仅用于运维/测试场景
    // 生产环境应避免调用；如确有需要，可改用 SCAN 命令
    throw new UnsupportedOperationException(
      "Redis 实现不支持 loadAll()，请使用按 ID 或按账号查询");
  }

  @Override
  public void streamByAppId(SessionId id, Consumer<AggregateRoot<SessionId>> processor) {
    if (id == null || processor == null) {
      return;
    }
    load(id).ifPresent(processor);
  }

  @Override
  public Optional<Session> findByPrimaryAccountId(UserNo primaryAccountId) {
    if (primaryAccountId == null) {
      return Optional.empty();
    }
    // 三渠道分别尝试一次（柜员可能在不同渠道有会话）
    for (AnnuityChannel channel : AnnuityChannel.values()) {
      Optional<Session> session = findActiveByPrimaryAccountIdAndChannel(primaryAccountId, channel);
      if (session.isPresent()) {
        return session;
      }
    }
    return Optional.empty();
  }

  @Override
  public Optional<Session> findActiveByPrimaryAccountIdAndChannel(UserNo primaryAccountId, AnnuityChannel channel) {
    if (primaryAccountId == null || channel == null) {
      return Optional.empty();
    }

    String sessionIdValue = redisTemplate.opsForValue().get(keyOfAccount(primaryAccountId, channel));
    if (sessionIdValue == null) {
      return Optional.empty();
    }

    Session session = load(new SessionId(sessionIdValue)).orElse(null);
    if (session == null) {
      return Optional.empty();
    }

    // 二次校验：索引可能因为 TTL 失效但未及时清理，确保返回的是 ACTIVE 会话
    if (session.status() != SessionStatus.ACTIVE) {
      return Optional.empty();
    }

    return Optional.of(session);
  }

  // ===============================
  // 内部工具方法
  // ===============================

  private void deleteById(SessionId id, UserNo primaryAccountId, AnnuityChannel channel) {
    redisTemplate.delete(keyOfId(id));
    if (primaryAccountId != null && channel != null) {
      redisTemplate.delete(keyOfAccount(primaryAccountId, channel));
    }
  }

  private Duration remainingTtl(Session session) {
    LocalDateTime now = LocalDateTime.now();
    if (!session.expiresAt().isAfter(now)) {
      // 已过期，给一个最小 TTL 让 Redis 接收写入
      return Duration.ofSeconds(1);
    }
    return Duration.between(now, session.expiresAt());
  }

  private String keyOfId(SessionId id) {
    return KEY_PREFIX_ID + id.value();
  }

  private String keyOfId(String sessionIdValue) {
    return KEY_PREFIX_ID + sessionIdValue;
  }

  private String keyOfAccount(UserNo primaryAccountId, AnnuityChannel channel) {
    return KEY_PREFIX_ACCOUNT + primaryAccountId.value() + ":channel:" + channel.name();
  }

  private String serialize(SessionDO doObj) {
    try {
      return objectMapper.writeValueAsString(doObj);
    } catch (Exception e) {
      throw new IllegalStateException("Session 序列化失败: " + doObj.getId(), e);
    }
  }

  private Session deserialize(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      SessionDO doObj = objectMapper.readValue(json, SessionDO.class);
      return converter.toDomain(doObj);
    } catch (Exception e) {
      log.error("Session 反序列化失败: {}", e.getMessage(), e);
      throw new IllegalStateException("Session 反序列化失败", e);
    }
  }
}
