package com.example.core.application.engine.callback;

/**
 * 外部协作回调结果（阶段 C 入站事件归一化）。
 * <p>
 * 本枚举描述「外部任务的执行结果」，与「业务申请单的流程终态」是两个维度：
 * 监听器拿到回调后既按本枚举闭环 {@code ExternalInteraction} / {@code StepExecution}，
 * 又用独立的审批结果字符串（APPROVED / REJECTED / WITHDRAWN）推进申请单。
 * 唯一映射见 {@code CallbackCorrelationService.resolveTargetStatus}——仅 {@code SUCCEEDED}
 * 使协作成功，其余结果（含驳回/撤回）均使协作与步骤进入 FAILED。
 */
public enum CallbackOutcome {
  /** 外部任务成功完成，协作与步骤进入 SUCCEEDED，流程继续推进。 */
  SUCCEEDED,
  /** 外部任务明确失败，协作与步骤进入 FAILED，不盲目推进。 */
  FAILED,
  /** 审批驳回：协作与步骤进入 FAILED，申请单由 {@code advanceByApprovalResult} 置为 REJECTED。 */
  REJECTED,
  /** 审批撤回：协作与步骤进入 FAILED，申请单由 {@code advanceByApprovalResult} 置为 WITHDRAWN。 */
  WITHDRAWN
}
