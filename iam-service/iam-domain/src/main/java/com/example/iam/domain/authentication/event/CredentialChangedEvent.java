package com.example.iam.domain.authentication.event;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 凭据修改事件
 */
public record CredentialChangedEvent(EventId eventId, LocalDateTime occurredOn,
                                     UserNo userId, CredentialType credentialType,
                                     UserNo changedBy) implements DomainEvent {

    public static CredentialChangedEvent of(UserNo userId, CredentialType credentialType, UserNo changedBy) {
        return new CredentialChangedEvent(EventId.generate(), LocalDateTime.now(),
            userId, credentialType, changedBy);
    }
}
