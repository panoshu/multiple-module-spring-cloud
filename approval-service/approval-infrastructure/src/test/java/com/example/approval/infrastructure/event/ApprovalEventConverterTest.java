package com.example.approval.infrastructure.event;

import com.example.approval.api.event.ApprovalInstanceApprovedEventDTO;
import com.example.approval.api.event.ApprovalInstanceCreatedEventDTO;
import com.example.approval.api.event.ApprovalInstanceRejectedEventDTO;
import com.example.approval.api.event.ApprovalInstanceWithdrawnEventDTO;
import com.example.approval.api.event.IntegrationEventTypes;
import com.example.approval.domain.event.ApprovalInstanceApproved;
import com.example.approval.domain.event.ApprovalInstanceCreated;
import com.example.approval.domain.event.ApprovalInstanceRejected;
import com.example.approval.domain.event.ApprovalInstanceWithdrawn;
import com.example.approval.types.ApprovalInstanceId;
import com.example.shared.primitives.identity.EventId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 审批集成事件转换器单元测试
 * 验证 4 个 Converter 的字段映射和类型标识
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
@DisplayName("审批集成事件转换器测试")
class ApprovalEventConverterTest {

    private static final ApprovalInstanceId INSTANCE_ID = ApprovalInstanceId.of(100L);
    private static final String BUSINESS_NO = "app-001";
    private static final String BUSINESS_TYPE = "ANNUITY";

    @Test
    @DisplayName("ApprovalInstanceCreatedEventConverter：supportedEventType 返回正确类型")
    void createdConverter_shouldReturnCorrectSupportedType() {
        ApprovalInstanceCreatedEventConverter converter = new ApprovalInstanceCreatedEventConverter();
        assertEquals(ApprovalInstanceCreated.class, converter.supportedEventType());
    }

    @Test
    @DisplayName("ApprovalInstanceCreatedEventConverter：integrationEventType 返回 IntegrationEventTypes 常量")
    void createdConverter_shouldReturnCorrectIntegrationType() {
        ApprovalInstanceCreatedEventConverter converter = new ApprovalInstanceCreatedEventConverter();
        assertEquals(IntegrationEventTypes.APPROVAL_INSTANCE_CREATED, converter.integrationEventType());
    }

    @Test
    @DisplayName("ApprovalInstanceCreatedEventConverter：toIntegrationEvent 正确映射字段")
    void createdConverter_shouldMapFieldsCorrectly() {
        // given
        ApprovalInstanceCreatedEventConverter converter = new ApprovalInstanceCreatedEventConverter();
        ApprovalInstanceCreated event = ApprovalInstanceCreated.of(INSTANCE_ID, BUSINESS_NO, BUSINESS_TYPE);

        // when
        Object result = converter.toIntegrationEvent(event);

        // then
        assertThat(result).isInstanceOf(ApprovalInstanceCreatedEventDTO.class);
        ApprovalInstanceCreatedEventDTO dto = (ApprovalInstanceCreatedEventDTO) result;
        assertEquals(event.eventId().value(), dto.eventId(),
                "eventId 应映射正确");
        assertEquals(String.valueOf(INSTANCE_ID.value()), dto.instanceId(),
                "instanceId 应映射为字符串形式");
        assertEquals(BUSINESS_NO, dto.businessNo(),
                "businessNo 应映射正确");
        assertEquals(BUSINESS_TYPE, dto.businessType(),
                "businessType 应映射正确");
        assertEquals(event.occurredOn(), dto.occurredOn(),
                "occurredOn 应映射正确");
    }

    @Test
    @DisplayName("ApprovalInstanceApprovedEventConverter：supportedEventType 返回正确类型")
    void approvedConverter_shouldReturnCorrectSupportedType() {
        ApprovalInstanceApprovedEventConverter converter = new ApprovalInstanceApprovedEventConverter();
        assertEquals(ApprovalInstanceApproved.class, converter.supportedEventType());
    }

    @Test
    @DisplayName("ApprovalInstanceApprovedEventConverter：integrationEventType 返回 IntegrationEventTypes 常量")
    void approvedConverter_shouldReturnCorrectIntegrationType() {
        ApprovalInstanceApprovedEventConverter converter = new ApprovalInstanceApprovedEventConverter();
        assertEquals(IntegrationEventTypes.APPROVAL_INSTANCE_APPROVED, converter.integrationEventType());
    }

    @Test
    @DisplayName("ApprovalInstanceApprovedEventConverter：toIntegrationEvent 正确映射字段")
    void approvedConverter_shouldMapFieldsCorrectly() {
        // given
        ApprovalInstanceApprovedEventConverter converter = new ApprovalInstanceApprovedEventConverter();
        ApprovalInstanceApproved event = ApprovalInstanceApproved.of(INSTANCE_ID, BUSINESS_NO, BUSINESS_TYPE);

        // when
        Object result = converter.toIntegrationEvent(event);

        // then
        assertThat(result).isInstanceOf(ApprovalInstanceApprovedEventDTO.class);
        ApprovalInstanceApprovedEventDTO dto = (ApprovalInstanceApprovedEventDTO) result;
        assertEquals(event.eventId().value(), dto.eventId(),
                "eventId 应映射正确");
        assertEquals(String.valueOf(INSTANCE_ID.value()), dto.instanceId(),
                "instanceId 应映射为字符串形式");
        assertEquals(BUSINESS_NO, dto.businessNo(),
                "businessNo 应映射正确");
        assertEquals(BUSINESS_TYPE, dto.businessType(),
                "businessType 应映射正确");
        assertEquals(event.occurredOn(), dto.occurredOn(),
                "occurredOn 应映射正确");
    }

    @Test
    @DisplayName("ApprovalInstanceRejectedEventConverter：supportedEventType 返回正确类型")
    void rejectedConverter_shouldReturnCorrectSupportedType() {
        ApprovalInstanceRejectedEventConverter converter = new ApprovalInstanceRejectedEventConverter();
        assertEquals(ApprovalInstanceRejected.class, converter.supportedEventType());
    }

    @Test
    @DisplayName("ApprovalInstanceRejectedEventConverter：integrationEventType 返回 IntegrationEventTypes 常量")
    void rejectedConverter_shouldReturnCorrectIntegrationType() {
        ApprovalInstanceRejectedEventConverter converter = new ApprovalInstanceRejectedEventConverter();
        assertEquals(IntegrationEventTypes.APPROVAL_INSTANCE_REJECTED, converter.integrationEventType());
    }

    @Test
    @DisplayName("ApprovalInstanceRejectedEventConverter：toIntegrationEvent 正确映射字段")
    void rejectedConverter_shouldMapFieldsCorrectly() {
        // given
        ApprovalInstanceRejectedEventConverter converter = new ApprovalInstanceRejectedEventConverter();
        ApprovalInstanceRejected event = ApprovalInstanceRejected.of(INSTANCE_ID, BUSINESS_NO, BUSINESS_TYPE);

        // when
        Object result = converter.toIntegrationEvent(event);

        // then
        assertThat(result).isInstanceOf(ApprovalInstanceRejectedEventDTO.class);
        ApprovalInstanceRejectedEventDTO dto = (ApprovalInstanceRejectedEventDTO) result;
        assertEquals(event.eventId().value(), dto.eventId(),
                "eventId 应映射正确");
        assertEquals(String.valueOf(INSTANCE_ID.value()), dto.instanceId(),
                "instanceId 应映射为字符串形式");
        assertEquals(BUSINESS_NO, dto.businessNo(),
                "businessNo 应映射正确");
        assertEquals(BUSINESS_TYPE, dto.businessType(),
                "businessType 应映射正确");
        assertEquals(event.occurredOn(), dto.occurredOn(),
                "occurredOn 应映射正确");
    }

    @Test
    @DisplayName("ApprovalInstanceWithdrawnEventConverter：supportedEventType 返回正确类型")
    void withdrawnConverter_shouldReturnCorrectSupportedType() {
        ApprovalInstanceWithdrawnEventConverter converter = new ApprovalInstanceWithdrawnEventConverter();
        assertEquals(ApprovalInstanceWithdrawn.class, converter.supportedEventType());
    }

    @Test
    @DisplayName("ApprovalInstanceWithdrawnEventConverter：integrationEventType 返回 IntegrationEventTypes 常量")
    void withdrawnConverter_shouldReturnCorrectIntegrationType() {
        ApprovalInstanceWithdrawnEventConverter converter = new ApprovalInstanceWithdrawnEventConverter();
        assertEquals(IntegrationEventTypes.APPROVAL_INSTANCE_WITHDRAWN, converter.integrationEventType());
    }

    @Test
    @DisplayName("ApprovalInstanceWithdrawnEventConverter：toIntegrationEvent 正确映射字段")
    void withdrawnConverter_shouldMapFieldsCorrectly() {
        // given
        ApprovalInstanceWithdrawnEventConverter converter = new ApprovalInstanceWithdrawnEventConverter();
        ApprovalInstanceWithdrawn event = ApprovalInstanceWithdrawn.of(INSTANCE_ID, BUSINESS_NO, BUSINESS_TYPE);

        // when
        Object result = converter.toIntegrationEvent(event);

        // then
        assertThat(result).isInstanceOf(ApprovalInstanceWithdrawnEventDTO.class);
        ApprovalInstanceWithdrawnEventDTO dto = (ApprovalInstanceWithdrawnEventDTO) result;
        assertEquals(event.eventId().value(), dto.eventId(),
                "eventId 应映射正确");
        assertEquals(String.valueOf(INSTANCE_ID.value()), dto.instanceId(),
                "instanceId 应映射为字符串形式");
        assertEquals(BUSINESS_NO, dto.businessNo(),
                "businessNo 应映射正确");
        assertEquals(BUSINESS_TYPE, dto.businessType(),
                "businessType 应映射正确");
        assertEquals(event.occurredOn(), dto.occurredOn(),
                "occurredOn 应映射正确");
    }

    @Test
    @DisplayName("EventId 应为 ULID 字符串")
    void eventId_shouldBeUlidString() {
        // given
        EventId eventId = EventId.generate();

        // then
        assertThat(eventId.value()).isNotBlank();
        assertThat(eventId.value()).hasSize(26);
    }

    @Test
    @DisplayName("occurredOn 应为当前时间附近")
    void occurredOn_shouldBeNearNow() {
        // given
        LocalDateTime before = LocalDateTime.now().minusSeconds(1);
        ApprovalInstanceCreated event = ApprovalInstanceCreated.of(INSTANCE_ID, BUSINESS_NO, BUSINESS_TYPE);
        LocalDateTime after = LocalDateTime.now().plusSeconds(1);

        // then
        assertThat(event.occurredOn()).isBetween(before, after);
    }
}
