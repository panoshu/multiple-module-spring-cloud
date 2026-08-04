package com.example.approval.domain.event;

import com.example.approval.types.ApprovalInstanceId;
import com.example.approval.types.NodeId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;

import java.time.LocalDateTime;

/**
 * 审批节点已完成事件
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record ApprovalNodeCompleted(
  EventId eventId,
  LocalDateTime occurredOn,
  ApprovalInstanceId instanceId,
  NodeId nodeId
) implements DomainEvent {

  /**
   * 静态工厂方法
   *
   * @param instanceId 审批实例ID
   * @param nodeId     审批节点ID
   * @return ApprovalNodeCompleted 实例
   */
  public static ApprovalNodeCompleted of(ApprovalInstanceId instanceId, NodeId nodeId) {
    return new ApprovalNodeCompleted(EventId.generate(), LocalDateTime.now(), instanceId, nodeId);
  }
}
