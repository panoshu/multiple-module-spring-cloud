package com.example.integration.infrastructure.core.trade.receipt;

import com.example.integration.domain.trade.gateway.OutboundSubmissionReceiptGateway;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * 出站提交幂等回执的 JDBC 实现（I-02）。
 * <p>表随首次使用自动创建，生产建议纳入正式 DDL/Flyway 管理。
 * 唯一键 (event_id, file_id) 提供跨实例并发幂等。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcOutboundSubmissionReceiptGateway implements OutboundSubmissionReceiptGateway {

  private final JdbcClient jdbcClient;

  @PostConstruct
  void ensureTable() {
    jdbcClient.sql("""
      CREATE TABLE IF NOT EXISTS t_integration_submission_receipt (
        event_id VARCHAR(64) NOT NULL,
        file_id VARCHAR(64) NOT NULL,
        status VARCHAR(16) NOT NULL,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (event_id, file_id)
      )
      """).update();
  }

  @Override
  public boolean tryAcquire(String eventId, String outboundFileId) {
    int inserted = jdbcClient.sql("""
      INSERT INTO t_integration_submission_receipt(event_id, file_id, status)
      VALUES (:eventId, :fileId, 'PROCESSING')
      ON CONFLICT (event_id, file_id) DO NOTHING
      """)
      .param("eventId", eventId)
      .param("fileId", outboundFileId)
      .update();
    return inserted == 1;
  }

  @Override
  public void markSuccess(String eventId, String outboundFileId) {
    jdbcClient.sql("""
      UPDATE t_integration_submission_receipt
      SET status = 'SUCCESS', updated_at = CURRENT_TIMESTAMP
      WHERE event_id = :eventId AND file_id = :fileId
      """)
      .param("eventId", eventId)
      .param("fileId", outboundFileId)
      .update();
  }

  @Override
  public void release(String eventId, String outboundFileId) {
    jdbcClient.sql("""
      DELETE FROM t_integration_submission_receipt
      WHERE event_id = :eventId AND file_id = :fileId AND status = 'PROCESSING'
      """)
      .param("eventId", eventId)
      .param("fileId", outboundFileId)
      .update();
  }
}
