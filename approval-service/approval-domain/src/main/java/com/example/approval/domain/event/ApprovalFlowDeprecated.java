package com.example.approval.domain.event;

import com.example.approval.types.ApprovalFlowId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;

import java.time.LocalDateTime;

/**
 * 审批流已废弃事件
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record ApprovalFlowDeprecated(
  EventId eventId,
  LocalDateTime occurredOn,
  ApprovalFlowId flowId
) implements DomainEvent {

  /**
   * 静态工厂方法
   *
   * @param flowId 审批流ID
   * @return ApprovalFlowDeprecated 实例
   */
  public static ApprovalFlowDeprecated of(ApprovalFlowId flowId) {
    return new ApprovalFlowDeprecated(EventId.generate(), LocalDateTime.now(), flowId);
  }
}
