package com.example.shared.event.store;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.event.contract.EventStore;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class JdbcEventStore implements EventStore {

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  @Override
  public void save(DomainEvent event, Object integrationEvent, String integrationType) {
    String sql = """
      INSERT INTO sys_event_store
        (event_id, event_type, integration_type, occurred_on, domain_payload, integration_payload)
      VALUES (?, ?, ?, ?, ?, ?)
      """;
    try {
      String domainPayload = objectMapper.writeValueAsString(event);
      String integrationPayload = integrationEvent != null
        ? objectMapper.writeValueAsString(integrationEvent)
        : null;
      String actualIntegrationType = integrationEvent != null ? integrationType : null;

      jdbcClient.sql(sql)
        .param(event.eventId().value())
        .param(event.eventType())
        .param(actualIntegrationType)
        .param(event.occurredOn())
        .param(domainPayload)
        .param(integrationPayload)
        .update();
    } catch (Exception e) {
      throw new RuntimeException("Error serializing or saving event", e);
    }
  }

  /**
   * 投递日志初始化（2026-11-08 批次4 修正事务策略）.
   *
   * <p>原 MANDATORY 与 DefaultEventBus 的无事务发布分支自相矛盾（bus 的 else
   * 分支明确支持无事务直发，但 MANDATORY 在此先行抛 IllegalTransactionStateException，
   * 分支不可达）——同流程的 save 也是 REQUIRED 语义，本方法曾独树一帜.</p>
   *
   * <p>SUPPORTS：有事务加入（保持 Outbox 原子性——业务回滚日志同生共死）；
   * 无事务独立提交（与 save 对齐），交付 bus 的双分支设计.</p>
   */
  @Override
  @Transactional(propagation = Propagation.SUPPORTS)
  public long initDispatchLog(String eventId, String channel) {
    KeyHolder keyHolder = new GeneratedKeyHolder();
    String sql = "INSERT INTO sys_event_dispatch_log (event_id, channel, status, created_at) VALUES (?, ?, 'PENDING', ?)";
    jdbcClient.sql(sql)
      .param(eventId)
      .param(channel)
      .param(LocalDateTime.now())
      .update(keyHolder, new String[]{"id"});
    // PG 的 useGeneratedKeys 会 RETURNING 整行（H2 仅回自增 id）——显式声明 key 列后
    // keyHolder 情况不一（驱动差异），双路兜底取 id
    Long id = keyHolder.getKeyAs(Long.class);
    if (id == null && keyHolder.getKeys() != null) {
      Object v = keyHolder.getKeys().get("id");
      id = v instanceof Number n ? n.longValue() : (v != null ? Long.parseLong(v.toString()) : null);
    }
    return Objects.requireNonNull(id, "插入 event dispatch log 失败");
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markSuccess(long logId) {
    jdbcClient.sql("UPDATE sys_event_dispatch_log SET status = 'SUCCESS', updated_at = NOW() WHERE id = ?")
      .param(logId).update();
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailure(long logId, Throwable ex) {
    String errorMsg = ex.getMessage();
    if (errorMsg != null && errorMsg.length() > 500) {
      errorMsg = errorMsg.substring(0, 500);
    }
    String sql = "UPDATE sys_event_dispatch_log SET status = 'FAILED', error_msg = ?, " +
      "retry_count = retry_count + 1, next_retry_at = ?, updated_at = NOW() WHERE id = ?";
    jdbcClient.sql(sql)
      .param(errorMsg)
      .param(LocalDateTime.now().plusMinutes(1))
      .param(logId).update();
  }

  /**
   * 认领时限：PROCESSING 状态超过此时长（处理方崩溃未回写）视为可回收.
   */
  private static final int CLAIM_TIMEOUT_MINUTES = 5;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<PendingEntry> claimPendingLogs(int batchSize) {
    LocalDateTime reclaimBefore = LocalDateTime.now().minusMinutes(CLAIM_TIMEOUT_MINUTES);

    // 1. 行级认领（FOR UPDATE SKIP LOCKED）：多实例并发扫描各自锁互不重叠的行，
    //    天然并行、无跨实例竞争空转；PROCESSING 超时行回收（崩溃兜底）
    String idSql = """
      SELECT l.id FROM sys_event_dispatch_log l
      WHERE (l.status IN ('PENDING', 'FAILED')
             OR (l.status = 'PROCESSING' AND l.updated_at < ?))
        AND (l.next_retry_at IS NULL OR l.next_retry_at <= ?)
        AND l.retry_count < 10
      ORDER BY l.id
      LIMIT ?
      FOR UPDATE SKIP LOCKED
      """;
    List<Long> ids = jdbcClient.sql(idSql)
      .param(reclaimBefore)
      .param(LocalDateTime.now())
      .param(batchSize)
      .query(Long.class).list();
    if (ids.isEmpty()) {
      return List.of();
    }

    // 2. 置 PROCESSING（认领标记；updated_at 刷新超时回收基准）
    String placeholders = ids.stream().map(_ -> "?").collect(Collectors.joining(", "));
    jdbcClient.sql("UPDATE sys_event_dispatch_log SET status = 'PROCESSING', updated_at = ? WHERE id IN ("
        + placeholders + ")")
      .param(LocalDateTime.now())
      .params(ids)
      .update();

    // 3. 取详情（payload 反序列化）
    String detailSql = """
      SELECT l.id, l.channel, l.retry_count,
             COALESCE(s.integration_payload, s.domain_payload) AS payload,
             COALESCE(s.integration_type, s.event_type) AS event_type
      FROM sys_event_dispatch_log l
      JOIN sys_event_store s ON l.event_id = s.event_id
      WHERE l.id IN (""" + placeholders + ")";
    return jdbcClient.sql(detailSql)
      .params(ids)
      .query((rs, _) -> {
        try {
          long logId = rs.getLong("id");
          String channel = rs.getString("channel");
          int retryCount = rs.getInt("retry_count");
          String payload = rs.getString("payload");
          String integrationType = rs.getString("event_type");
          // 直接用 Map 反序列化，不依赖具体类（避免 Class.forName）
          Map<String, Object> integrationEvent = objectMapper.readValue(payload, new TypeReference<>() {
          });
          return new PendingEntry(logId, integrationEvent, channel, integrationType, retryCount);
        } catch (Exception e) {
          log.error("Failed to deserialize pending entry", e);
          return null;
        }
      })
      .list().stream().filter(Objects::nonNull).toList();
  }
}
