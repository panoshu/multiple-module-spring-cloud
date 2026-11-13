package com.example.core.application.engine.callback;

/** 外部协作回调结果（阶段 C 入站事件归一化）。 */
public enum CallbackOutcome {
  /** 外部任务成功完成，协作与步骤进入 SUCCEEDED，流程继续推进。 */
  SUCCEEDED,
  /** 外部任务明确失败，协作与步骤进入 FAILED，不盲目推进。 */
  FAILED,
  /** 审批驳回，协作完成、流程进入 REJECTED。 */
  REJECTED,
  /** 审批撤回，协作完成、流程进入 WITHDRAWN。 */
  WITHDRAWN
}
