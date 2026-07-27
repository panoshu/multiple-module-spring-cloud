package com.example.iam.domain.authorization.event;

import com.example.iam.types.PermissionRuleId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 权限规则已启用事件。
 *
 * <p>由 {@code PermissionRule.enable} 方法注册,触发后:
 * <ul>
 *   <li>应用层发布集成事件通知其他服务刷新权限缓存</li>
 *   <li>sa-token Token-Session 中相关用户的权限缓存重建</li>
 *   <li>记录权限规则变更审计日志</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record PermissionRuleEnabledEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    PermissionRuleId ruleId,
    String ruleCode,
    UserNo operator
) implements DomainEvent {

  public static PermissionRuleEnabledEvent of(PermissionRuleId ruleId, String ruleCode,
                                              UserNo operator) {
    return new PermissionRuleEnabledEvent(EventId.generate(), LocalDateTime.now(),
        ruleId, ruleCode, operator);
  }
}
