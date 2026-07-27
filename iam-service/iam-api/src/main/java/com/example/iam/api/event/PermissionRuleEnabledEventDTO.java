package com.example.iam.api.event;

import java.time.LocalDateTime;

/**
 * 权限规则启用集成事件 DTO。
 * <p>对应 iam-domain 的 PermissionRuleEnabled 领域事件,跨服务发布到 MQ。
 *
 * @author iam-service
 */
public record PermissionRuleEnabledEventDTO(
        String eventId,
        Long ruleId,
        String ruleCode,
        String operator,
        LocalDateTime occurredOn
) {
}
