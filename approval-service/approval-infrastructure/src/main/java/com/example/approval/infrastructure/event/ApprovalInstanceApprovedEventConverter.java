package com.example.approval.infrastructure.event;

import com.example.approval.api.event.ApprovalInstanceApprovedEventDTO;
import com.example.approval.api.event.IntegrationEventTypes;
import com.example.approval.domain.event.ApprovalInstanceApproved;
import com.example.shared.domain.event.IntegrationEventConverter;
import org.springframework.stereotype.Component;

/**
 * 审批实例已通过领域事件 -> 集成事件转换器。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@Component
public class ApprovalInstanceApprovedEventConverter
        implements IntegrationEventConverter<ApprovalInstanceApproved> {

    @Override
    public Class<ApprovalInstanceApproved> supportedEventType() {
        return ApprovalInstanceApproved.class;
    }

    @Override
    public Object toIntegrationEvent(ApprovalInstanceApproved event) {
        return new ApprovalInstanceApprovedEventDTO(
                event.eventId().value(),
                String.valueOf(event.instanceId().value()),
                event.businessNo(),
                event.businessType(),
                event.occurredOn()
        );
    }

    @Override
    public String integrationEventType() {
        return IntegrationEventTypes.APPROVAL_INSTANCE_APPROVED;
    }
}
