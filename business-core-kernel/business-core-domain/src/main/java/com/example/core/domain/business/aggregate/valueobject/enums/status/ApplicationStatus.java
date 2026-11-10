package com.example.core.domain.business.aggregate.valueobject.enums.status;

/**
 * 业务申请宏观状态（spec §8.1）。
 * <p>
 * 状态迁移由 {@link #canTransitTo} 统一约束，终态不可再迁移。
 * 审批拒绝与业务撤回属于「明确终止行为」，由聚合根单独处理，不套用一般迁移表。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/13 12:35
 */
public enum ApplicationStatus {
  DRAFT,
  RUNNING,
  WAITING_USER,
  WAITING_EXTERNAL,
  FAILED,
  COMPLETED,
  REJECTED,
  WITHDRAWN,
  CANCELLED,

  /** 存量数据使用的「处理中」状态，语义等同 RUNNING，阶段 5 清理时移除。 */
  @Deprecated
  PROCESSING;

  /** 把存量兼容状态归一为当前语义状态。 */
  public ApplicationStatus normalized() {
    return this == PROCESSING ? RUNNING : this;
  }

  public boolean isTerminal() {
    return switch (normalized()) {
      case COMPLETED, REJECTED, WITHDRAWN, CANCELLED -> true;
      default -> false;
    };
  }

  /** spec §8.1 合法迁移表。 */
  public boolean canTransitTo(ApplicationStatus target) {
    if (target == null) {
      return false;
    }
    ApplicationStatus from = normalized();
    ApplicationStatus to = target.normalized();
    return switch (from) {
      case DRAFT -> to == RUNNING || to == CANCELLED;
      case RUNNING -> to == WAITING_USER || to == WAITING_EXTERNAL || to == RUNNING
        || to == COMPLETED || to == FAILED || to == CANCELLED;
      case WAITING_USER -> to == RUNNING || to == CANCELLED || to == WITHDRAWN;
      case WAITING_EXTERNAL -> to == RUNNING || to == FAILED || to == REJECTED
        || to == WITHDRAWN || to == CANCELLED;
      case FAILED -> to == RUNNING || to == CANCELLED;
      case COMPLETED, REJECTED, WITHDRAWN, CANCELLED -> false;
      case PROCESSING -> false; // 已归一，不会到达
    };
  }
}
