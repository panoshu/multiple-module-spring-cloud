package com.example.core.domain.engine.aggregate.valueobject.enums.status;

/**
 * 步骤执行状态（spec §8.2）。
 * <p>
 * 与现有 {@code StepExecutionStatus}（Handler 返回值：SUCCESS/SUSPEND_ASYNC_WAIT/FAILED）
 * 语义不同，故独立命名。Handler 返回值只表达领域结果，应用服务据此与重试策略
 * 决定本状态迁移，不直接写库。
 */
public enum StepExecutionState {
  PENDING,
  RUNNING,
  WAITING_EXTERNAL,
  SUCCEEDED,
  FAILED,
  RETRY_WAIT,
  SKIPPED,
  CANCELLED;

  public boolean isTerminal() {
    return this == SUCCEEDED || this == SKIPPED || this == CANCELLED;
  }

  public boolean canTransitTo(StepExecutionState target) {
    if (target == null) {
      return false;
    }
    return switch (this) {
      case PENDING -> target == RUNNING || target == SKIPPED || target == CANCELLED;
      case RUNNING -> target == WAITING_EXTERNAL || target == SUCCEEDED || target == FAILED
        || target == RETRY_WAIT || target == CANCELLED;
      case WAITING_EXTERNAL -> target == RUNNING || target == SUCCEEDED
        || target == FAILED || target == CANCELLED;
      case RETRY_WAIT, FAILED -> target == RUNNING || target == CANCELLED;
      case SUCCEEDED, SKIPPED, CANCELLED -> false;
    };
  }
}
