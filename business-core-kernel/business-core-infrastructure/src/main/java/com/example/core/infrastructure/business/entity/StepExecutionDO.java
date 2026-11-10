package com.example.core.infrastructure.business.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 步骤执行 DO，对应聚合根 {@code StepExecution}。
 * <p>
 * 主键为 BIGINT，由 {@code IdService} 号段策略生成（§16.3），与存量三个字符串强类型 ID 区分。
 * createTime/updateTime 由 Converter 从领域对象映射，不使用 ORM 自动填充。
 */
@Data
@Table("t_step_execution")
public class StepExecutionDO {

  @Id(keyType = KeyType.None)
  private Long id;

  private String applicationId;
  private String definitionVersion;
  private String stepCode;
  private Integer attempt;
  private String requestNo;
  private String status;
  private LocalDateTime startedAt;
  private LocalDateTime endedAt;
  private String errorCode;
  private String errorSummary;
  private String checkpointRef;

  private String createdBy;
  private String updatedBy;
  private LocalDateTime createTime;
  private LocalDateTime updateTime;

  @Column(isLogicDelete = true)
  private Boolean deleted;

  @Column(version = true)
  private Integer version;
}
