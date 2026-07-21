package com.example.core.domain.aggregate.valueobject;

import java.util.List;

/**
 * PipelineExecutionResult
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 08:20
 */
public record PipelineExecutionResult(
  boolean isSuccess,
  List<ActionExecutionRecord> actionRecords // 记录每个 action 的明细
) {
  /**
   * 定义动作在管道中的最终生命周期状态
   */
  public enum ActionStatus {
    SUCCESS,          // 同步执行成功
    FAILURE,          // 同步执行失败 (业务阻断或抛出异常)
    SKIPPED,          // 因未满足 SpEL 条件被安全跳过
    ASYNC_DISPATCHED  // 异步动作已成功派发到线程池
  }

  /**
   * 单个 Action 的执行记录
   */
  public record ActionExecutionRecord(
    String actionName,
    ActionStatus status,
    long costTimeMs,
    ExtensionExecutionResult metrics
  ) {
    // ==================== 语义化工厂方法 ====================

    public static ActionExecutionRecord skipped(String actionName) {
      return new ActionExecutionRecord(actionName, ActionStatus.SKIPPED, 0, null);
    }

    public static ActionExecutionRecord async(String actionName, long costTimeMs) {
      return new ActionExecutionRecord(actionName, ActionStatus.ASYNC_DISPATCHED, costTimeMs, null);
    }

    public static ActionExecutionRecord success(String actionName, long costTimeMs, ExtensionExecutionResult metrics) {
      return new ActionExecutionRecord(actionName, ActionStatus.SUCCESS, costTimeMs, metrics);
    }

    public static ActionExecutionRecord failure(String actionName, long costTimeMs, ExtensionExecutionResult metrics) {
      return new ActionExecutionRecord(actionName, ActionStatus.FAILURE, costTimeMs, metrics);
    }
  }
}
