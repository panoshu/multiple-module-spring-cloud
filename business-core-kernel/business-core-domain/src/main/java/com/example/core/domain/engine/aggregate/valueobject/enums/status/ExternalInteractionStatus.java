package com.example.core.domain.engine.aggregate.valueobject.enums.status;

/**
 * 外部协作状态（spec §8.3）。
 * <p>
 * {@code UNKNOWN} 表示请求已发出但结果无法确定（如 HTTP 超时）：进入后必须先查询或对账
 * 确认 {@code SUCCEEDED}/{@code FAILED}，不得直接重新派发或安排重试，避免重复创建外部任务。
 */
public enum ExternalInteractionStatus {
  PENDING_DISPATCH,
  DISPATCHING,
  WAITING_CALLBACK,
  SUCCEEDED,
  FAILED,
  RETRY_WAIT,
  UNKNOWN,
  CANCELLED;

  public boolean isTerminal() {
    return this == SUCCEEDED || this == CANCELLED;
  }

  public boolean canTransitTo(ExternalInteractionStatus target) {
    if (target == null) {
      return false;
    }
    return switch (this) {
      case PENDING_DISPATCH -> target == DISPATCHING || target == CANCELLED;
      case DISPATCHING -> target == WAITING_CALLBACK || target == SUCCEEDED
        || target == FAILED || target == UNKNOWN || target == CANCELLED;
      case WAITING_CALLBACK -> target == SUCCEEDED || target == FAILED
        || target == UNKNOWN || target == RETRY_WAIT || target == CANCELLED;
      case FAILED -> target == RETRY_WAIT || target == CANCELLED;
      case RETRY_WAIT -> target == DISPATCHING || target == CANCELLED;
      case UNKNOWN -> target == SUCCEEDED || target == FAILED || target == CANCELLED;
      case SUCCEEDED, CANCELLED -> false;
    };
  }
}
