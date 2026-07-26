package com.example.iam.domain.authentication.event;

import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 二次授权会话已过期事件。
 *
 * <p>由 {@code SecondaryAuthSession.markExpired} 注册,触发后:
 * <ul>
 *   <li>应用层调用 {@code StpBranchUtil.kickout(tellerId)} 踢出柜员会话</li>
 *   <li>通知柜员授权会话已过期,需重新发起</li>
 *   <li>记录审计日志</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record SecondaryAuthExpiredEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    SecondaryAuthSessionId sessionId,
    Long tellerId
) implements DomainEvent {

  public static SecondaryAuthExpiredEvent of(SecondaryAuthSessionId sessionId, Long tellerId) {
    return new SecondaryAuthExpiredEvent(EventId.generate(), LocalDateTime.now(),
        sessionId, tellerId);
  }
}
