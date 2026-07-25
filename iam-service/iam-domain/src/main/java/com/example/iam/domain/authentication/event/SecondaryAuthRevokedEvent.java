package com.example.iam.domain.authentication.event;

import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 二次授权撤销事件
 */
public record SecondaryAuthRevokedEvent(EventId eventId, LocalDateTime occurredOn,
                                        SecondaryAuthSessionId sessionId,
                                        UserNo revokedBy) implements DomainEvent {

    public static SecondaryAuthRevokedEvent of(SecondaryAuthSessionId sessionId, UserNo revokedBy) {
        return new SecondaryAuthRevokedEvent(EventId.generate(), LocalDateTime.now(), sessionId, revokedBy);
    }
}
