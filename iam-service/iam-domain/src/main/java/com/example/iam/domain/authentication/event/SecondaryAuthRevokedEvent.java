package com.example.iam.domain.authentication.event;

import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 二次授权已撤销事件。
 *
 * <p>由 {@code SecondaryAuthSession.revoke} 注册,触发后:
 * <ul>
 *   <li>应用层调用 {@code StpBranchUtil.kickout(tellerId)} 踢出柜员会话</li>
 *   <li>通知柜员授权已被撤销</li>
 *   <li>记录审计日志</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record SecondaryAuthRevokedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    SecondaryAuthSessionId sessionId,
    Long tellerId,
    String reason,
    UserNo operator
) implements DomainEvent {

  public static SecondaryAuthRevokedEvent of(SecondaryAuthSessionId sessionId, Long tellerId,
                                              String reason, UserNo operator) {
    return new SecondaryAuthRevokedEvent(EventId.generate(), LocalDateTime.now(),
        sessionId, tellerId, reason, operator);
  }
}
