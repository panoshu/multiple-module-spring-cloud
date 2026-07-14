package com.example.approval.domain.event;

import com.example.approval.types.ApprovalInstanceId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 审批实例已驳回事件
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record ApprovalInstanceRejected(
        EventId eventId,
        LocalDateTime occurredOn,
        ApprovalInstanceId instanceId
) implements DomainEvent {

    /**
     * 静态工厂方法
     *
     * @param instanceId 审批实例ID
     * @return ApprovalInstanceRejected 实例
     */
    public static ApprovalInstanceRejected of(ApprovalInstanceId instanceId) {
        return new ApprovalInstanceRejected(EventId.generate(), LocalDateTime.now(), instanceId);
    }
}