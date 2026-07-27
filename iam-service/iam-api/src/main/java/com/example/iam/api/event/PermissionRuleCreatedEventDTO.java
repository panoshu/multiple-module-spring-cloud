package com.example.iam.api.event;

import java.time.LocalDateTime;

/**
 * 权限规则创建集成事件 DTO。
 * <p>对应 iam-domain 的 PermissionRuleCreated 领域事件,跨服务发布到 MQ。
 *
 * @author iam-service
 */
public record PermissionRuleCreatedEventDTO(
        String eventId,
        Long ruleId,
        String ruleCode,
        String subjectType,
        String subjectId,
        String businessCode,
        String overrideMode,
        Integer priority,
        String operator,
        LocalDateTime occurredOn
) {
}
