package com.example.core.application.engine.dispatch;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.repository.ExternalInteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 外部协作派发器（spec §9.2 阶段 B「事务外派发」编排骨架，批次 28）。
 * <p>
 * 扫描 {@code PENDING_DISPATCH} 协作，逐条：认领为 {@code DISPATCHING} 并落库（崩溃可恢复）
 * → 按 {@link InteractionType} 分派给 {@link InteractionDispatchHandler} 在事务外调用外部服务
 * → 明确受理（拿到 externalTaskId）则推进 {@code WAITING_CALLBACK}；未受理 / 无匹配 handler
 * 则保持 {@code DISPATCHING}，由恢复任务回收为 UNKNOWN 对账，绝不盲目重派。
 * <p>
 * 单条失败被隔离，不影响同批其他协作；本方法不含事务注解，由调用方（AFTER_COMMIT 监听器
 * 或恢复任务）保证已在事务外。批次 28 仅建立编排骨架与 SPI，尚未注册任何真实 handler、
 * 未接入监听器/Job，故生产行为等价于「认领到 DISPATCHING」，E2E 零回归。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalInteractionDispatcher {

  private static final String OPERATOR = "SYSTEM";

  private final ExternalInteractionRepository repository;
  private final List<InteractionDispatchHandler> handlers;

  /**
   * 派发全部待派发协作。
   *
   * @return 成功推进到 WAITING_CALLBACK 的条数
   */
  public int dispatchPending() {
    List<ExternalInteraction> candidates =
      repository.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH);
    if (candidates == null || candidates.isEmpty()) {
      return 0;
    }
    int advanced = 0;
    for (ExternalInteraction interaction : candidates) {
      try {
        if (dispatchOne(interaction)) {
          advanced++;
        }
      } catch (Exception e) {
        log.error("派发外部协作失败（单条隔离）: interactionId={}, requestNo={}, type={}",
          interaction.id(), interaction.requestNo(), interaction.type(), e);
      }
    }
    return advanced;
  }

  private boolean dispatchOne(ExternalInteraction interaction) {
    // 阶段 B 第 2 步：认领为 DISPATCHING 并先落库，服务崩溃后可从该状态恢复
    interaction.markDispatching(OPERATOR);
    repository.save(interaction);

    InteractionDispatchHandler handler = findHandler(interaction.type());
    if (handler == null) {
      log.debug("暂无 {} 类型的派发处理器，协作保持 DISPATCHING: requestNo={}",
        interaction.type(), interaction.requestNo());
      return false;
    }

    // 阶段 B 第 3 步：事务外调用外部服务
    String externalTaskId = handler.dispatch(interaction);
    if (externalTaskId == null || externalTaskId.isBlank()) {
      log.warn("外部服务未明确受理，协作保持 DISPATCHING 待对账: interactionId={}, requestNo={}",
        interaction.id(), interaction.requestNo());
      return false;
    }

    // 阶段 B 第 4 步：明确受理，保存关联并进入等待回调
    interaction.markWaitingCallback(externalTaskId, OPERATOR);
    repository.save(interaction);
    return true;
  }

  private InteractionDispatchHandler findHandler(InteractionType type) {
    if (handlers == null || type == null) {
      return null;
    }
    return handlers.stream()
      .filter(handler -> type.equals(handler.supportedType()))
      .findFirst()
      .orElse(null);
  }
}
