package com.example.shared.id.segment.repository;

import com.example.shared.exception.SystemException;
import com.example.shared.id.errorcode.IdErrorCode;
import com.example.shared.id.segment.model.IdSegment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
public class JdbcSegmentRepository implements SegmentRepository {

  private final JdbcClient jdbcClient;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public IdSegment fetchNextSegment(String sequenceKey) {
    try {
      // 1. 尝试更新号段 (乐观更新)
      boolean updated = updateMaxId(sequenceKey);

      // 2. 如果更新失败(行不存在)，则初始化记录并重试
      if (!updated) {
        handleInitialization(sequenceKey);
      }

      // 3. 查询当前号段信息
      return queryCurrentSegment(sequenceKey);

    } catch (SystemException e) {
      throw e; // 已经是包装好的异常，直接抛出
    } catch (Exception e) {
      log.error("Critical Error: Failed to retrieve ID segment for key: {}", sequenceKey, e);
      // 【修正】使用错误码
      throw new SystemException(IdErrorCode.ID_GEN_ERROR, e)
        .withLogDetail("Repository error for key: %s".formatted(sequenceKey));
    }
  }

  private boolean updateMaxId(String sequenceKey) {
    String sql = "UPDATE t_id_generator SET max_id = max_id + step, update_time = CURRENT_TIMESTAMP WHERE seq_key = :key";
    int rows = jdbcClient.sql(sql)
      .param("key", sequenceKey)
      .update();
    return rows > 0;
  }

  private void handleInitialization(String sequenceKey) {
    log.info("SequenceKey [{}] not found. Attempting to initialize.", sequenceKey);

    insertInitialRecord(sequenceKey);

    // 初始化后必须再次执行 update，因为初始化的 max_id 是 0
    boolean retryUpdated = updateMaxId(sequenceKey);
    if (!retryUpdated) {
      // 【修正】使用错误码
      throw new SystemException(IdErrorCode.ID_GEN_ERROR)
        .withLogDetail("Failed to update sequence after init for key: " + sequenceKey);
    }
  }

  private void insertInitialRecord(String sequenceKey) {
    try {
      // 默认步长 1000，从 0 开始
      String sql = "INSERT INTO t_id_generator (seq_key, max_id, step, create_time, update_time) VALUES (:key, 0, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
      jdbcClient.sql(sql)
        .param("key", sequenceKey)
        .update();
    } catch (Exception e) {
      // 忽略主键冲突，说明有并发初始化
      log.warn("Concurrent initialization detected for key: {}. Safe to ignore.", sequenceKey);
    }
  }

  private IdSegment queryCurrentSegment(String sequenceKey) {
    String sql = "SELECT max_id, step FROM t_id_generator WHERE seq_key = :key";
    return jdbcClient.sql(sql)
      .param("key", sequenceKey)
      .query((rs, _) -> {
        long currentMaxId = rs.getLong("max_id");
        long step = rs.getLong("step");
        return new IdSegment(currentMaxId - step, currentMaxId, step);
      })
      .single();
  }
}
