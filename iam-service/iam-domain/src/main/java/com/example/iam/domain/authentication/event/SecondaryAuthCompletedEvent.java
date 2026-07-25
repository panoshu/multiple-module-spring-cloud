package com.example.iam.domain.authentication.event;

import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStrategyType;
import com.example.iam.types.BranchUserId;
import com.example.iam.types.InternetUserId;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 二次授权完成事件
 */
public record SecondaryAuthCompletedEvent(EventId eventId, LocalDateTime occurredOn,
                                          SecondaryAuthSessionId sessionId,
                                          BranchUserId branchUserId,
                                          InternetUserId internetUserId,
                                          SecondaryAuthStrategyType strategyType) implements DomainEvent {

    public static SecondaryAuthCompletedEvent of(SecondaryAuthSessionId sessionId,
                                                 BranchUserId branchUserId,
                                                 InternetUserId internetUserId,
                                                 SecondaryAuthStrategyType strategyType) {
        return new SecondaryAuthCompletedEvent(EventId.generate(), LocalDateTime.now(),
            sessionId, branchUserId, internetUserId, strategyType);
    }
}
