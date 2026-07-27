package com.example.iam.domain.authorization.event;

import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.OverrideMode;
import com.example.iam.domain.authorization.aggregate.valueobject.SubjectType;
import com.example.iam.types.PermissionRuleId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 权限规则已创建事件。
 *
 * <p>由 {@code PermissionRule.create} 工厂方法注册,触发后:
 * <ul>
 *   <li>应用层发布集成事件通知其他服务刷新权限缓存</li>
 *   <li>记录权限规则变更审计日志</li>
 *   <li>sa-token Token-Session 中相关用户的权限缓存失效(可选,异步处理)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record PermissionRuleCreatedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    PermissionRuleId ruleId,
    String ruleCode,
    SubjectType subjectType,
    String subjectId,
    BusinessCode businessCode,
    OverrideMode overrideMode,
    Integer priority,
    UserNo operator
) implements DomainEvent {

  public static PermissionRuleCreatedEvent of(PermissionRuleId ruleId, String ruleCode,
                                              SubjectType subjectType, String subjectId,
                                              BusinessCode businessCode,
                                              OverrideMode overrideMode,
                                              Integer priority,
                                              UserNo operator) {
    return new PermissionRuleCreatedEvent(EventId.generate(), LocalDateTime.now(),
        ruleId, ruleCode, subjectType, subjectId, businessCode, overrideMode,
        priority, operator);
  }
}
