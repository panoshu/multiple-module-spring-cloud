package com.example.approval.domain.event;

import com.example.approval.types.ApprovalInstanceId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 审批实例已通过事件
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public record ApprovalInstanceApproved(
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
     * @return ApprovalInstanceApproved 实例
     */
    public static ApprovalInstanceApproved of(ApprovalInstanceId instanceId,
                                               String businessNo, String businessType) {
        return new ApprovalInstanceApproved(EventId.generate(), LocalDateTime.now(),
                instanceId, businessNo, businessType);
    }
}
