package com.example.core.infrastructure.business.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部协作 DO，对应聚合根 {@code ExternalInteraction}（spec §16.1）。
 * <p>
 * 主键与关联的步骤执行 ID 均为 BIGINT；createTime/updateTime 由 Converter 从领域对象映射。
 */
@Data
@Table("t_business_external_interaction")
public class ExternalInteractionDO {

  @Id(keyType = KeyType.None)
  private Long id;

  private String applicationId;
  private Long stepExecutionId;
  private String interactionType;
  private String requestNo;
  private String externalTaskId;
  private String status;
  private String resultRef;
  private Integer retryCount;
  // 列名遵循 §16.1 的 (status, next_retry_time) 索引，与领域字段 nextRetryAt() 由 Converter 映射
  private LocalDateTime nextRetryTime;
  private String lastError;
  private LocalDateTime completedAt;

  private String createdBy;
  private String updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  @Column(isLogicDelete = true)
  private Boolean deleted;

  @Column(version = true)
  private Integer version;
}
