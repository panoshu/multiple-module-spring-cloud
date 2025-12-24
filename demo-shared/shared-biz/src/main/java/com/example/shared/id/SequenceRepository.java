package com.example.shared.id;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Repository
public class SequenceRepository {

  private final JdbcClient jdbcClient;
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

  // 默认步长：因为是客户维度，并发不会太夸张，步长20足够，避免重启浪费太多
  private static final int DEFAULT_STEP = 20;

  public SequenceRepository(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /**
   * 原子获取下一个号段
   * 利用 MySQL 的 ON DUPLICATE KEY UPDATE 特性
   */
  @Transactional
  public IdSegment nextSegment(String bizCode, String custNo) {
    String dateStr = LocalDate.now().format(DATE_FMT);
    // 组装联合主键：PAY:20251122:CUST001
    String seqKey = bizCode + ":" + dateStr + ":" + custNo;

    // 核心 SQL：
    // 1. 如果不存在，插入初始值 (step)
    // 2. 如果存在，current_max_id = current_max_id + step
    // 3. LAST_INSERT_ID(val) 用于让后续的 SELECT LAST_INSERT_ID() 获取到更新后的值
    var sql = """
            INSERT INTO t_cust_seq_conf (seq_key, current_max_id, step)
            VALUES (:key, :step, :step)
            ON DUPLICATE KEY UPDATE
            current_max_id = LAST_INSERT_ID(current_max_id + step)
            """;

    jdbcClient.sql(sql)
      .param("key", seqKey)
      .param("step", DEFAULT_STEP)
      .update();

    // 获取原子更新后的最大值
    long maxId = jdbcClient.sql("SELECT LAST_INSERT_ID()").query(Long.class).single();

    // 计算本次申请到的号段范围
    return new IdSegment(maxId - DEFAULT_STEP + 1, maxId);
  }
}
