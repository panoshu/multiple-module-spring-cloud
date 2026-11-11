package com.example.core.domain.engine.repository;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
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
}
