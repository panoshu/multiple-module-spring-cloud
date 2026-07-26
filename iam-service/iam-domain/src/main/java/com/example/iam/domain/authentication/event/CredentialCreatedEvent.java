package com.example.iam.domain.authentication.event;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.types.CredentialId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 凭据已创建事件。
 *
 * <p>由 {@code Credential.create} 注册,触发后:
 * <ul>
 *   <li>同步默认凭据到 sa-token (可选)</li>
 *   <li>记录凭据创建审计日志</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record CredentialCreatedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    CredentialId credentialId,
    Long ownerId,
    String ownerType,
    CredentialType credentialType
) implements DomainEvent {

  public static CredentialCreatedEvent of(CredentialId credentialId, Long ownerId,
                                           String ownerType, CredentialType credentialType) {
    return new CredentialCreatedEvent(EventId.generate(), LocalDateTime.now(),
        credentialId, ownerId, ownerType, credentialType);
  }
}
