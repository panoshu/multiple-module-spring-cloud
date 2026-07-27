package com.example.iam.domain.authorization.event;

import com.example.iam.types.PlanDelegationId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 计划代办关系已撤销事件。
 *
 * <p>由 {@code PlanDelegation.revoke} 方法注册,触发后:
 * <ul>
 *   <li>应用层发布集成事件通知其他服务刷新代办关系缓存</li>
 *   <li>sa-token Token-Session 中相关用户的权限缓存失效</li>
 *   <li>记录代办关系撤销审计日志</li>
 *   <li>通知被授权方计划经办(可选)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record PlanDelegationRevokedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    PlanDelegationId delegationId,
    String delegationCode,
    String delegateePlanNo,
    String reason,
    UserNo operator
) implements DomainEvent {

  public static PlanDelegationRevokedEvent of(PlanDelegationId delegationId, String delegationCode,
                                                String delegateePlanNo,
                                                String reason, UserNo operator) {
    return new PlanDelegationRevokedEvent(EventId.generate(), LocalDateTime.now(),
        delegationId, delegationCode, delegateePlanNo, reason, operator);
  }
}
