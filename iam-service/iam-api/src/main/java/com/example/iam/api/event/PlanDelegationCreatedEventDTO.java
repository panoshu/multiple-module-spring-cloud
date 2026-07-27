package com.example.iam.api.event;

import java.time.LocalDateTime;

/**
 * 计划代办创建集成事件 DTO。
 * <p>对应 iam-domain 的 PlanDelegationCreated 领域事件,跨服务发布到 MQ。
 *
 * @author iam-service
 */
public record PlanDelegationCreatedEventDTO(
        String eventId,
        Long delegationId,
        String delegationCode,
        String delegatorPlanNo,
        String delegateePlanNo,
        String delegationType,
        String operator,
        LocalDateTime occurredOn
) {
}
