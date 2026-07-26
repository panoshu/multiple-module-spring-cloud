package com.example.iam.domain.authentication.event;

import com.example.iam.types.UserId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 用户已禁用事件。
 *
 * <p>由 {@code User.disable} 注册,触发后:
 * <ul>
 *   <li>Credential 聚合监听并撤销该用户所有凭据</li>
 *   <li>sa-token 踢出该用户所有渠道会话</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record UserDisabledEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    UserId userId,
    String reason,
    UserNo operator
) implements DomainEvent {

  public static UserDisabledEvent of(UserId userId, String reason, UserNo operator) {
    return new UserDisabledEvent(EventId.generate(), LocalDateTime.now(),
        userId, reason, operator);
  }
}
