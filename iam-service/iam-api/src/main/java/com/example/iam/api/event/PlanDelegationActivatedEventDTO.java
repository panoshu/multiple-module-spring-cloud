package com.example.iam.api.event;

import java.time.LocalDateTime;

/**
 * 计划代办激活集成事件 DTO。
 * <p>对应 iam-domain 的 PlanDelegationActivated 领域事件,跨服务发布到 MQ。
 *
 * @author iam-service
 */
public record PlanDelegationActivatedEventDTO(
        String eventId,
        Long delegationId,
        String delegationCode,
        String delegateePlanNo,
        String operator,
        LocalDateTime occurredOn
) {
}
