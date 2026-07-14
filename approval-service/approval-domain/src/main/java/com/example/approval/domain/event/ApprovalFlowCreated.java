package com.example.approval.domain.event;

import com.example.approval.types.ApprovalFlowId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 审批流已创建事件
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record ApprovalFlowCreated(
        EventId eventId,
        LocalDateTime occurredOn,
        ApprovalFlowId flowId
) implements DomainEvent {

    /**
     * 静态工厂方法
     *
     * @param flowId 审批流ID
     * @return ApprovalFlowCreated 实例
     */
    public static ApprovalFlowCreated of(ApprovalFlowId flowId) {
        return new ApprovalFlowCreated(EventId.generate(), LocalDateTime.now(), flowId);
    }
}