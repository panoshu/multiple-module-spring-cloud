package com.example.iam.domain.authentication.event;

import com.example.iam.types.UserId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 用户已启用事件。
 *
 * <p>由 {@code User.enable} 注册,触发后允许该用户重新登录。
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record UserEnabledEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    UserId userId,
    UserNo operator
) implements DomainEvent {

  public static UserEnabledEvent of(UserId userId, UserNo operator) {
    return new UserEnabledEvent(EventId.generate(), LocalDateTime.now(),
        userId, operator);
  }
}
