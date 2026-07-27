package com.example.iam.api.event;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 二次授权完成集成事件 DTO。
 * <p>对应 iam-domain 的 SecondaryAuthCompleted 领域事件,跨服务发布到 MQ。
 *
 * @author iam-service
 */
public record SecondaryAuthCompletedEventDTO(
        String eventId,
        Long sessionId,
        Long tellerId,
        Long approverId,
        String planId,
        String customerNo,
        Set<String> permissionSnapshot,
        LocalDateTime authorizedAt,
        LocalDateTime expireAt,
        LocalDateTime occurredOn
) {
}
