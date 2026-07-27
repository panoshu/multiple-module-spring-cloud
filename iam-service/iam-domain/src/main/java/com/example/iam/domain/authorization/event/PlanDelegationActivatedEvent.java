package com.example.iam.domain.authorization.event;

import com.example.iam.types.PlanDelegationId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 计划代办关系已激活事件。
 *
 * <p>由 {@code PlanDelegation.create} 工厂方法在状态变为 ACTIVE 时注册
 * (create 直接进入 ACTIVE 状态时,同时触发 Created 与 Activated 两个事件;
 * 未来若支持"待激活→激活"流程,activate() 方法将仅触发本事件),触发后:
 * <ul>
 *   <li>应用层发布集成事件通知其他服务刷新代办关系缓存</li>
 *   <li>sa-token Token-Session 中相关用户的权限缓存重建</li>
 *   <li>记录代办关系激活审计日志</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record PlanDelegationActivatedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    PlanDelegationId delegationId,
    String delegationCode,
    String delegateePlanNo,
    UserNo operator
) implements DomainEvent {

  public static PlanDelegationActivatedEvent of(PlanDelegationId delegationId, String delegationCode,
                                                  String delegateePlanNo,
                                                  UserNo operator) {
    return new PlanDelegationActivatedEvent(EventId.generate(), LocalDateTime.now(),
        delegationId, delegationCode, delegateePlanNo, operator);
  }
}
