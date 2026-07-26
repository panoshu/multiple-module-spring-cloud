package com.example.iam.domain.authentication.event;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.types.CredentialId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;

import java.time.LocalDateTime;

/**
 * 凭据已过期事件。
 *
 * <p>由 {@code Credential.markExpired} 注册,触发后:
 * <ul>
 *   <li>记录凭据过期审计日志</li>
 *   <li>用户下次登录时提示凭据已过期</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record CredentialExpiredEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    CredentialId credentialId,
    Long ownerId,
    CredentialType credentialType
) implements DomainEvent {

  public static CredentialExpiredEvent of(CredentialId credentialId, Long ownerId,
                                          CredentialType credentialType) {
    return new CredentialExpiredEvent(EventId.generate(), LocalDateTime.now(),
        credentialId, ownerId, credentialType);
  }
}
