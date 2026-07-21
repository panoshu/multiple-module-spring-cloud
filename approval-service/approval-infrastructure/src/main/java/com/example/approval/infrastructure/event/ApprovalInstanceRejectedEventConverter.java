package com.example.approval.infrastructure.event;

import com.example.approval.api.event.ApprovalInstanceRejectedEventDTO;
import com.example.approval.api.event.IntegrationEventTypes;
import com.example.approval.domain.event.ApprovalInstanceRejected;
import com.example.shared.domain.event.IntegrationEventConverter;
import org.springframework.stereotype.Component;

/**
 * 审批实例已驳回领域事件 -> 集成事件转换器。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@Component
public class ApprovalInstanceRejectedEventConverter
        implements IntegrationEventConverter<ApprovalInstanceRejected> {

    @Override
    public Class<ApprovalInstanceRejected> supportedEventType() {
        return ApprovalInstanceRejected.class;
    }

    @Override
    public Object toIntegrationEvent(ApprovalInstanceRejected event) {
        return new ApprovalInstanceRejectedEventDTO(
                event.eventId().value(),
                String.valueOf(event.instanceId().value()),
                event.businessNo(),
                event.businessType(),
                event.occurredOn()
        );
    }

    @Override
    public String integrationEventType() {
        return IntegrationEventTypes.APPROVAL_INSTANCE_REJECTED;
    }
}
