package com.example.core.infrastructure.business.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 事件消费回执 DO（spec §7.4 / §16.1）。
 * <p>
 * 基础设施持久化模型，不是领域聚合；(event_id, consumer) 联合唯一约束保证幂等。
 * 主键按 §16.3 使用 BIGINT，由 {@code IdService} 号段策略生成。
 */
@Data
@Table("t_business_event_receipt")
public class EventReceiptDO {

  @Id(keyType = KeyType.None)
  private Long id;

  private String eventId;
  private String consumer;
  private String status;
  private String errorSummary;

  private String createdBy;
  private String updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  @Column(isLogicDelete = true)
  private Boolean deleted;

  @Column(version = true)
  private Integer version;
}
