package com.example.iam.domain.authentication.event;

import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 二次授权已发起事件。
 *
 * <p>由 {@code SecondaryAuthSession.initiate} 注册,触发后:
 * <ul>
 *   <li>应用层通知经办人(短信/邮件/推送)</li>
 *   <li>记录审计日志</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record SecondaryAuthInitiatedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    SecondaryAuthSessionId sessionId,
    Long tellerId,
    Long approverId,
    String customerNo,
    String planId
) implements DomainEvent {

  public static SecondaryAuthInitiatedEvent of(SecondaryAuthSessionId sessionId,
                                                Long tellerId, Long approverId,
                                                String customerNo, String planId) {
    return new SecondaryAuthInitiatedEvent(EventId.generate(), LocalDateTime.now(),
        sessionId, tellerId, approverId, customerNo, planId);
  }
}
