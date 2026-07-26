package com.example.iam.domain.authentication.event;

import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 二次授权已完成事件。
 *
 * <p>由 {@code SecondaryAuthSession.authorize} 注册,触发后:
 * <ul>
 *   <li>应用层将权限快照写入 sa-token Token-Session</li>
 *   <li>通知柜员授权已通过,可继续办理业务</li>
 *   <li>记录审计日志</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record SecondaryAuthCompletedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    SecondaryAuthSessionId sessionId,
    Long tellerId,
    Long approverId,
    String planId,
    LocalDateTime authorizedAt,
    LocalDateTime expireAt
) implements DomainEvent {

  public static SecondaryAuthCompletedEvent of(SecondaryAuthSessionId sessionId,
                                                Long tellerId, Long approverId, String planId,
                                                LocalDateTime authorizedAt, LocalDateTime expireAt) {
    return new SecondaryAuthCompletedEvent(EventId.generate(), LocalDateTime.now(),
        sessionId, tellerId, approverId, planId, authorizedAt, expireAt);
  }
}
