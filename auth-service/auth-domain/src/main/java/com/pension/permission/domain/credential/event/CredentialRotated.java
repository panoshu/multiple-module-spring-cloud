package com.pension.permission.domain.credential.event;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.types.CredentialId;

import java.time.LocalDateTime;

/**
 * CredentialIssued
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/8/1 19:56
 */
public record CredentialRotated(
  CredentialId credentialId,
  EventId eventId,
  LocalDateTime occurredOn,
  UserNo createdBy
) implements DomainEvent {

  public static CredentialRotated of(CredentialId credentialId, UserNo createdBy) {
    return new CredentialRotated(credentialId, EventId.generate(), LocalDateTime.now(), createdBy);
  }
}
