package com.example.iam.api.event;

import java.time.LocalDateTime;

/**
 * 用户已禁用集成事件 DTO。
 * <p>对应 iam-domain 的 UserDisabled 领域事件,跨服务发布到 MQ。
 *
 * @author iam-service
 */
public record UserDisabledEventDTO(
        String eventId,
        Long userId,
        String reason,
        String operator,
        LocalDateTime occurredOn
) {
}
