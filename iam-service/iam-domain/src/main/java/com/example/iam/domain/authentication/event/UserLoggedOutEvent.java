package com.example.iam.domain.authentication.event;

import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 用户登出事件
 */
public record UserLoggedOutEvent(EventId eventId, LocalDateTime occurredOn,
                                 UserNo userId, ChannelType channel) implements DomainEvent {

    public static UserLoggedOutEvent of(UserNo userId, ChannelType channel) {
        return new UserLoggedOutEvent(EventId.generate(), LocalDateTime.now(), userId, channel);
    }
}
