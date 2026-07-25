package com.example.iam.domain.authentication.event;

import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 用户登录成功事件
 */
public record UserLoggedInEvent(EventId eventId, LocalDateTime occurredOn,
                                UserNo userId, ChannelType channel,
                                String ipAddress, String userAgent) implements DomainEvent {

    public static UserLoggedInEvent of(UserNo userId, ChannelType channel,
                                       String ipAddress, String userAgent) {
        return new UserLoggedInEvent(EventId.generate(), LocalDateTime.now(),
            userId, channel, ipAddress, userAgent);
    }
}
