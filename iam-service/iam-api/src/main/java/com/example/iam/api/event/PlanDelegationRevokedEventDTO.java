package com.example.iam.api.event;

import java.time.LocalDateTime;

/**
 * 计划代办撤销集成事件 DTO。
 * <p>对应 iam-domain 的 PlanDelegationRevoked 领域事件,跨服务发布到 MQ。
 *
 * @author iam-service
 */
public record PlanDelegationRevokedEventDTO(
        String eventId,
        Long delegationId,
        String delegationCode,
        String delegateePlanNo,
        String reason,
        String operator,
        LocalDateTime occurredOn
) {
}
