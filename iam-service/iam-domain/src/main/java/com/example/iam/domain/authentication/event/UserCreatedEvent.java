package com.example.iam.domain.authentication.event;

import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.types.UserId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 用户已创建事件。
 *
 * <p>由 {@code User.create} 工厂方法注册,触发后:
 * <ul>
 *   <li>应用层创建默认 Credential(初始密码)</li>
 *   <li>通知外部系统(可选)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record UserCreatedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    UserId userId,
    ChannelType channelType,
    String loginName,
    String displayName
) implements DomainEvent {

  public static UserCreatedEvent of(UserId userId, ChannelType channelType,
                                     String loginName, String displayName) {
    return new UserCreatedEvent(EventId.generate(), LocalDateTime.now(),
        userId, channelType, loginName, displayName);
  }
}
