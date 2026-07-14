package com.example.shared.event.store;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class JdbcEventStore implements EventStore {

  private final JdbcClient jdbcClient;
  private final ObjectMapper objectMapper;

  @Override
  public void save(DomainEvent event) {
    String sql = "INSERT INTO sys_event_store (event_id, event_type, payload, occurred_on) VALUES (?, ?, ?, ?)";
    try {
      String payload = objectMapper.writeValueAsString(event);
      jdbcClient.sql(sql)
        .param(event.eventId())
        .param(event.eventType())
        .param(payload)
        .param(event.occurredOn())
        .update();
    } catch (Exception e) {
      throw new RuntimeException("Error serializing or saving event", e);
    }
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public long initDispatchLog(String eventId, String channel) {
    KeyHolder keyHolder = new GeneratedKeyHolder();

    String sql = "INSERT INTO sys_event_dispatch_log (event_id, channel, status, created_at) VALUES (?, ?, 'PENDING', ?)";
    jdbcClient.sql(sql)
      .param(eventId)
      .param(channel)
      .param(LocalDateTime.now())
      .update(keyHolder);

    return Objects.requireNonNull(
      keyHolder.getKeyAs(Long.class),
      "插入 event dispatch log 失败，未能获取到数据库生成的主键 ID"
    );
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markSuccess(long logId) {
    jdbcClient.sql("UPDATE sys_event_dispatch_log SET status = 'SUCCESS', updated_at = NOW() WHERE id = ?")
      .param(logId)
      .update();
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

    LocalDateTime nextRetry = LocalDateTime.now().plusMinutes(1);
    jdbcClient.sql(sql)
      .param(errorMsg)
      .param(nextRetry)
      .param(logId)
      .update();
  }

  @Override
  public List<PendingEntry> findPendingLogs(int batchSize) {
    String sql = """
                SELECT l.id, l.channel, l.retry_count, s.payload, s.event_type
                FROM sys_event_dispatch_log l
                JOIN sys_event_store s ON l.event_id = s.event_id
                WHERE l.status IN ('PENDING', 'FAILED')
                  AND (l.next_retry_at IS NULL OR l.next_retry_at <= NOW())
                  AND l.retry_count < 10
                LIMIT ?
                """;

    return jdbcClient.sql(sql)
      .param(batchSize)
      .query((rs, _) -> {
        try {
          long logId = rs.getLong("id");
          String channel = rs.getString("channel");
          int retryCount = rs.getInt("retry_count");
          String payload = rs.getString("payload");
          String eventType = rs.getString("event_type");

          Class<?> eventClass = Class.forName(eventType);
          DomainEvent event = (DomainEvent) objectMapper.readValue(payload, eventClass);

          return new PendingEntry(logId, event, channel, retryCount);
        } catch (Exception e) {
          return null;
        }
      })
      .list()
      .stream()
      .filter(Objects::nonNull)
      .toList();
  }
}
