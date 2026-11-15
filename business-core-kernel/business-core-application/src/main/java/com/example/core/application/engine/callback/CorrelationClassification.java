package com.example.core.application.engine.callback;

/**
 * 回调关联分类（spec §9.2 阶段 C / §18「乱序或不合规回调按可重试/可对账/人工处理分类」）。
 */
public enum CorrelationClassification {
  /** 命中协作且校验通过：协作与步骤状态已闭环，流程可继续推进。 */
  APPLIED,
  /** 事件缺少关联键（过渡期旧构造，如 E2E 模拟器）：回退既有 fileTaskId/businessNo 关联路径。 */
  NO_CORRELATION,
  /** 有 requestNo 但找不到协作：可对账，不盲目推进。 */
  UNMATCHED,
  /** 找到协作但 matches/类型/状态不符或已终态：乱序或重复，分类记录、不推进。 */
  OUT_OF_ORDER
}
