package com.example.core.application.engine.service;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.gateway.ApprovalIntegrationGateway;
import com.example.core.domain.engine.repository.ExternalInteractionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

/**
 * 外部协作恢复服务（spec §19.2）。
 * <p>
 * 提供四类恢复动作：认领 PENDING_DISPATCH、回收长期滞留 DISPATCHING、
 * 处理 RETRY_WAIT 到期、对 UNKNOWN 执行外部查询与对账。
 * 每条记录独立处理并保存，单条失败（含并发重复认领导致的非法状态迁移）被隔离，
 * 不影响同批其他记录；每条记录的「状态变更 + 保存 + 事件发布」包在独立本地事务中，
 * 保证原子性且失败时只回滚该条、不阻断同批其余记录；所有动作只推进协作状态，
 * 不重复触发外部调用。外部审批状态查询保持在事务外，避免远程 IO 拉长事务。
 * <p>
 * 完整的事务外派发与 {@code DISPATCHING → WAITING_CALLBACK → SUCCEEDED} 回调链路
 * 依赖 file/approval 的 requestNo 幂等与查询契约，留待阶段 3 接入（§11.4/§12.3）。
 */
@Slf4j
@Service
public class ExternalInteractionRecoveryService {

  private static final String OPERATOR = "SYSTEM";
  private static final String STALE_ERROR = "派发超时，结果未知，需对账";
  private static final String APPROVAL_APPROVED = "APPROVED";
  private static final String APPROVAL_REJECTED = "REJECTED";
  private static final String APPROVAL_WITHDRAWN = "WITHDRAWN";

  private final ExternalInteractionRepository repository;
  private final ApprovalIntegrationGateway approvalGateway;
  private final TransactionTemplate transactionTemplate;

  public ExternalInteractionRecoveryService(ExternalInteractionRepository repository,
                                            ApprovalIntegrationGateway approvalGateway,
                                            PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.approvalGateway = approvalGateway;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  /** 认领待派发协作：PENDING_DISPATCH → DISPATCHING（§9.2 阶段 B 第 2 步）。 */
  public int claimPendingDispatch() {
    return process(repository.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH),
      "认领待派发协作", interaction -> interaction.markDispatching(OPERATOR));
  }

  /** 回收长时间停留在 DISPATCHING 的协作：结果按 UNKNOWN 处理（§18.3/§19.2）。 */
  public int reclaimStaleDispatching(LocalDateTime updatedBefore) {
    if (updatedBefore == null) {
      return 0;
    }
    return process(repository.findStaleDispatching(updatedBefore),
      "回收滞留派发协作", interaction -> interaction.markUnknown(STALE_ERROR, OPERATOR));
  }

  /** 处理到期的重试等待：RETRY_WAIT → DISPATCHING 重新派发（§19.2）。 */
  public int processDueRetries(LocalDateTime now) {
    if (now == null) {
      return 0;
    }
    return process(repository.findPendingRetry(now),
      "处理到期重试", interaction -> interaction.dispatchAgain(OPERATOR));
  }

  /**
   * 对 UNKNOWN 协作执行外部查询与对账（§19.2）。
   * <p>
   * 当前仅 APPROVAL 类型且已保存 externalTaskId 的协作具备查询能力；
   * FILE_* 与缺少关联信息的协作保留 UNKNOWN 待阶段 3 对账，绝不盲目重派（§8.3）。
   */
  public int reconcileUnknown() {
    List<ExternalInteraction> candidates =
      repository.findByStatus(ExternalInteractionStatus.UNKNOWN);
    if (candidates == null || candidates.isEmpty()) {
      return 0;
    }
    int confirmed = 0;
    for (ExternalInteraction interaction : candidates) {
      try {
        if (reconcileOne(interaction)) {
          confirmed++;
        }
      } catch (Exception e) {
        log.error("UNKNOWN 协作对账失败: interactionId={}, requestNo={}",
          interaction.id(), interaction.requestNo(), e);
      }
    }
    return confirmed;
  }

  private boolean reconcileOne(ExternalInteraction interaction) {
    if (interaction.type() != InteractionType.APPROVAL
      || !StringUtils.hasText(interaction.externalTaskId())) {
      log.debug("UNKNOWN 协作暂不具备对账条件: interactionId={}, type={}",
        interaction.id(), interaction.type());
      return false;
    }
    // 外部查询在事务外，避免远程 IO 拉长本地事务
    String status = approvalGateway.queryApprovalStatus(interaction.externalTaskId());
    boolean approved = APPROVAL_APPROVED.equals(status);
    boolean terminal = approved || APPROVAL_REJECTED.equals(status)
      || APPROVAL_WITHDRAWN.equals(status);
    if (!terminal) {
      // PENDING / APPROVING：外部仍在处理，保留 UNKNOWN 交由下一轮对账
      return false;
    }
    String resultRef = "approval:" + status;
    transactionTemplate.executeWithoutResult(tx -> {
      if (approved) {
        interaction.confirmSucceededAfterReconciliation(resultRef, OPERATOR);
      } else {
        interaction.confirmFailedAfterReconciliation(resultRef, OPERATOR);
      }
      repository.save(interaction);
    });
    return true;
  }

  private int process(List<ExternalInteraction> interactions, String label,
                      Consumer<ExternalInteraction> action) {
    if (interactions == null || interactions.isEmpty()) {
      return 0;
    }
    int succeeded = 0;
    for (ExternalInteraction interaction : interactions) {
      try {
        transactionTemplate.executeWithoutResult(tx -> {
          action.accept(interaction);
          repository.save(interaction);
        });
        succeeded++;
      } catch (Exception e) {
        log.error("{}失败: interactionId={}, requestNo={}", label,
          interaction.id(), interaction.requestNo(), e);
      }
    }
    return succeeded;
  }
}
