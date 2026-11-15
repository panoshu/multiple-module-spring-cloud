package com.example.core.domain.engine.repository;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.shared.domain.repository.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 外部协作仓储契约（spec §7.3 / §16.1 / §17）。
 * <p>
 * 除通用 {@link Repository} 契约外，提供按 requestNo / externalTaskId 的关联查询、
 * 待重试扫描（配合 {@code (status, next_retry_time)} 索引）与按申请单查询。
 */
public interface ExternalInteractionRepository
  extends Repository<ExternalInteraction, ExternalInteractionId> {

  Optional<ExternalInteraction> findByRequestNo(String requestNo);

  Optional<ExternalInteraction> findByExternalTaskId(InteractionType type, String externalTaskId);

  List<ExternalInteraction> findPendingRetry(LocalDateTime retryBefore);

  List<ExternalInteraction> findByApplication(ApplicationId applicationId);

  /**
   * 按状态扫描协作记录，供恢复任务认领 PENDING_DISPATCH 与对账 UNKNOWN（§19.2）。
   */
  List<ExternalInteraction> findByStatus(ExternalInteractionStatus status);

  /**
   * 扫描长时间停留在 DISPATCHING 的协作，供恢复任务回收为 UNKNOWN（§19.2）。
   *
   * @param updatedBefore 更新时间上界（含）
   */
  List<ExternalInteraction> findStaleDispatching(LocalDateTime updatedBefore);
}
