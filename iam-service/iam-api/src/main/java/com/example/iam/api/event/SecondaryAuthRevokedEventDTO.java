package com.example.iam.api.event;

import java.time.LocalDateTime;

/**
 * 二次授权撤销集成事件 DTO。
 * <p>对应 iam-domain 的 SecondaryAuthRevoked 领域事件,跨服务发布到 MQ。
 *
 * @author iam-service
 */
public record SecondaryAuthRevokedEventDTO(
        String eventId,
        Long sessionId,
        Long tellerId,
        String reason,
        String operator,
        LocalDateTime occurredOn
) {
}
