package com.example.iam.domain.authorization.event;

import com.example.iam.domain.authorization.aggregate.valueobject.DelegationType;
import com.example.iam.types.PlanDelegationId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 计划代办关系已创建事件。
 *
 * <p>由 {@code PlanDelegation.create} 工厂方法注册,触发后:
 * <ul>
 *   <li>应用层发布集成事件通知其他服务刷新代办关系缓存</li>
 *   <li>记录代办关系变更审计日志</li>
 *   <li>通知被授权方计划经办(可选)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record PlanDelegationCreatedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    PlanDelegationId delegationId,
    String delegationCode,
    String delegatorPlanNo,
    String delegateePlanNo,
    DelegationType delegationType,
    UserNo operator
) implements DomainEvent {

  public static PlanDelegationCreatedEvent of(PlanDelegationId delegationId, String delegationCode,
                                              String delegatorPlanNo, String delegateePlanNo,
                                              DelegationType delegationType,
                                              UserNo operator) {
    return new PlanDelegationCreatedEvent(EventId.generate(), LocalDateTime.now(),
        delegationId, delegationCode, delegatorPlanNo, delegateePlanNo,
        delegationType, operator);
  }
}
