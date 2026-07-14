package com.example.approval.domain.event;

import com.example.approval.types.ApprovalInstanceId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 审批实例已创建事件
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record ApprovalInstanceCreated(
        EventId eventId,
        LocalDateTime occurredOn,
        ApprovalInstanceId instanceId
) implements DomainEvent {

    /**
     * 静态工厂方法
     *
     * @param instanceId 审批实例ID
     * @return ApprovalInstanceCreated 实例
     */
    public static ApprovalInstanceCreated of(ApprovalInstanceId instanceId) {
        return new ApprovalInstanceCreated(EventId.generate(), LocalDateTime.now(), instanceId);
    }
}