package com.pension.permission.domain.credential.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.CredentialId;

import java.time.LocalDateTime;

/**
 * 凭证撤销只影响"以后还能不能用这条凭证认证"，不直接踢掉已有登录态——那是AccountFrozen的职责
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 20:08
 */
public record CredentialRevoked(
  CredentialId credentialId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {
  public static CredentialRevoked of(CredentialId credentialId, UserNo createdBy) {
    return new CredentialRevoked(credentialId, EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
