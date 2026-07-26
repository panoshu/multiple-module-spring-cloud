package com.example.iam.domain.authentication.event;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.types.CredentialId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.EventId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;

/**
 * 凭据已修改事件。
 *
 * <p>由 {@code Credential.change} 注册,触发后:
 * <ul>
 *   <li>记录凭据变更审计日志</li>
 *   <li>sa-token 不强制踢出(用户可继续使用旧会话)</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
public record CredentialChangedEvent(
    EventId eventId,
    LocalDateTime occurredOn,
    CredentialId credentialId,
    Long ownerId,
    CredentialType credentialType,
    UserNo operator
) implements DomainEvent {

  public static CredentialChangedEvent of(CredentialId credentialId, Long ownerId,
                                          CredentialType credentialType, UserNo operator) {
    return new CredentialChangedEvent(EventId.generate(), LocalDateTime.now(),
        credentialId, ownerId, credentialType, operator);
  }
}
