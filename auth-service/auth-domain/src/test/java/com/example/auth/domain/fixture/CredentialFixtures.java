package com.example.auth.domain.fixture;

import com.example.core.types.UserNo;
import com.example.core.types.temporal.ValidityPeriod;
import com.example.auth.domain.credential.aggregate.PasswordCredential;
import com.example.auth.domain.credential.aggregate.UKeyCredential;
import com.example.auth.domain.credential.valueobject.owner.UserCredentialOwner;
import com.example.auth.types.CredentialId;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

/**
 * credential 域测试数据工厂。
 */
public final class CredentialFixtures {

  private CredentialFixtures() {
  }

  public static Clock fixedClock(String instant) {
    return Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
  }

  public static PasswordCredential activePasswordCredential(String userNo) {
    return PasswordCredential.create(
      new CredentialId("c-pwd-1"),
      UserNo.of(userNo),
      "hashed-password-001",
      Set.of(),
      UserNo.of("creator-1"),
      fixedClock("2026-01-01T00:00:00Z"));
  }

  public static UKeyCredential activeUKeyCredential(String userNo) {
    return UKeyCredential.create(
      new CredentialId("c-ukey-1"),
      new UserCredentialOwner(UserNo.of(userNo)),
      "key-serial-001",
      Set.of(),
      ValidityPeriod.between(
        LocalDateTime.now(),
        LocalDateTime.now().plusDays(365)),
      UserNo.of("creator-1"));
  }
}
