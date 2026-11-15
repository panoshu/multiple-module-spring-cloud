package com.example.core.application.engine.dispatch;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;

/**
 * 外部协作派发处理器 SPI（spec §9.2 阶段 B「事务外派发」）。
 * <p>
 * 每种 {@link InteractionType} 一个实现，由 {@link ExternalInteractionDispatcher}
 * 按类型分派，在事务外调用 file-service / approval-service。返回外部任务 ID 表示
 * 明确受理；返回 {@code null} 表示未受理 / 未接线（协作保持 DISPATCHING，
 * 由恢复任务回收为 UNKNOWN 对账，绝不盲目重派）。
 * <p>
 * 实现负责从聚合与只读配置重建派发参数，不得持有外部服务 DTO。
 */
public interface InteractionDispatchHandler {

  InteractionType supportedType();

  /**
   * 事务外派发。
   *
   * @param interaction 已认领为 DISPATCHING 的协作记录
   * @return externalTaskId（fileTaskId / approvalInstanceId），未受理返回 null
   */
  String dispatch(ExternalInteraction interaction);
}
