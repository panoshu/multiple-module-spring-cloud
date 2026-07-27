package com.example.iam.api.event;

import java.time.LocalDateTime;

/**
 * 用户已启用集成事件 DTO。
 * <p>对应 iam-domain 的 UserEnabled 领域事件,跨服务发布到 MQ。
 *
 * @author iam-service
 */
public record UserEnabledEventDTO(
        String eventId,
        Long userId,
        String operator,
        LocalDateTime occurredOn
) {
}
