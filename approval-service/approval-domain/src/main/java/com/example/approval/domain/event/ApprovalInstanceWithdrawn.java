package com.example.approval.domain.event;

import com.example.approval.types.ApprovalInstanceId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;

import java.time.LocalDateTime;

/**
 * 审批实例已撤回事件
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record ApprovalInstanceWithdrawn(
  EventId eventId,
  LocalDateTime occurredOn,
  ApprovalInstanceId instanceId,
  String businessNo,
  String businessType
) implements DomainEvent {

  /**
   * 静态工厂方法
   *
   * @param instanceId   审批实例ID
   * @param businessNo   业务单号
   * @param businessType 业务类型
   * @return ApprovalInstanceWithdrawn 实例
   */
  public static ApprovalInstanceWithdrawn of(ApprovalInstanceId instanceId,
                                             String businessNo, String businessType) {
    return new ApprovalInstanceWithdrawn(EventId.generate(), LocalDateTime.now(),
      instanceId, businessNo, businessType);
  }
}
