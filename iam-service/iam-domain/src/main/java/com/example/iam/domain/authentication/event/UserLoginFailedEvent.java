package com.example.iam.domain.authentication.event;

import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 用户登录失败事件
 */
public record UserLoginFailedEvent(EventId eventId, LocalDateTime occurredOn,
                                   String loginName, ChannelType channel,
                                   String failReason, String ipAddress,
                                   String userAgent) implements DomainEvent {

    public static UserLoginFailedEvent of(String loginName, ChannelType channel,
                                          String failReason, String ipAddress, String userAgent) {
        return new UserLoginFailedEvent(EventId.generate(), LocalDateTime.now(),
            loginName, channel, failReason, ipAddress, userAgent);
    }
}
