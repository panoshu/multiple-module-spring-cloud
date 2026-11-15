package com.example.core.application.engine.inbound;

/**
 * 审批结果入站消息（kernel 自有契约）。
 * <p>
 * adapter 层把 approval-service 的三类审批结果事件统一映射为本消息，
 * 应用层只感知“审批结果”这一业务语义，不依赖 approval-service 的
 * 事件类型命名与拆分方式。
 *
 * @param eventId    事件幂等 ID
 * @param instanceId 审批实例 ID
 * @param businessNo 业务单号
 * @param requestNo  外部协作幂等键（可为空）
 * @param result     审批结果
 * @author business-core-kernel
 */
public record ApprovalResultMessage(
  String eventId,
  String instanceId,
  String businessNo,
  String requestNo,
  ApprovalResult result
) {

  /**
   * 审批结果语义。
   */
  public enum ApprovalResult {
    APPROVED,
    REJECTED,
    WITHDRAWN
  }
}
