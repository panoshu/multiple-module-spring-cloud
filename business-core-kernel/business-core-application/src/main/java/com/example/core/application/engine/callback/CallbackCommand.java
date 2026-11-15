package com.example.core.application.engine.callback;

import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;

/**
 * 外部协作回调关联命令（阶段 C 入站事件归一化后的内核入参）。
 *
 * @param requestNo       外部协作幂等键（优先关联键，来自事件 DTO 的 requestNo；过渡期可空）
 * @param externalTaskId  外部任务 ID（回退关联键，如 fileTaskId / approvalInstanceId）
 * @param expectedType    期望的协作类型，用于交叉校验，防止串单
 * @param outcome         回调结果
 * @param resultRef       外部结果引用（成功时作为 resultRef，失败时作为错误摘要）
 * @param operator        操作人
 */
public record CallbackCommand(
  String requestNo,
  String externalTaskId,
  InteractionType expectedType,
  CallbackOutcome outcome,
  String resultRef,
  String operator
) {
}
