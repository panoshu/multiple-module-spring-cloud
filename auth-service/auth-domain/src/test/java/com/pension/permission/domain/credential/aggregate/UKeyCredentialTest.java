package com.pension.permission.domain.credential.aggregate;

import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.credential.enumeration.CredentialStatus;
import com.pension.permission.domain.credential.enumeration.CredentialType;
import com.pension.permission.domain.credential.event.UKeyRotated;
import com.pension.permission.domain.fixture.CredentialFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UKeyCredential 聚合根测试")
class UKeyCredentialTest {

  @Nested
  @DisplayName("创建 create")
  class CreateTest {

    @Test
    @DisplayName("应创建 ACTIVE 状态的 UKey 凭证")
    void shouldCreateActiveCredential() {
      var credential = CredentialFixtures.activeUKeyCredential("user-1");

      assertThat(credential.status()).isEqualTo(CredentialStatus.ACTIVE);
      assertThat(credential.type()).isEqualTo(CredentialType.U_KEY);
      assertThat(credential.keySerial()).isEqualTo("key-serial-001");
    }

    @Test
    @DisplayName("keySerial 为空应抛 DomainException")
    void shouldThrowWhenKeySerialBlank() {
      assertThatThrownBy(() -> UKeyCredential.create(
        new com.pension.permission.types.CredentialId("c-ukey-x"),
        new com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner(UserNo.of("user-1")),
        "  ",
        java.util.Set.of(),
        com.example.shared.valueobject.ValidityPeriod.infinite(),
        UserNo.of("creator-1")))
        .isInstanceOf(DomainException.class);
    }
  }

  @Nested
  @DisplayName("轮换 rotate")
  class RotateTest {

    @Test
    @DisplayName("应更新 keySerial 并注册事件")
    void shouldRotateUKey() {
      var credential = CredentialFixtures.activeUKeyCredential("user-1");

      credential.rotate("new-serial-002", UserNo.of("user-1"));

      assertThat(credential.keySerial()).isEqualTo("new-serial-002");
      assertThat(credential.domainEvents()).anyMatch(e -> e instanceof UKeyRotated);
    }

    @Test
    @DisplayName("新旧 keySerial 相同应抛 DomainException")
    void shouldThrowWhenSameAsOld() {
      var credential = CredentialFixtures.activeUKeyCredential("user-1");

      assertThatThrownBy(() -> credential.rotate("key-serial-001", UserNo.of("user-1")))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("撤销后轮换应抛 DomainException")
    void shouldThrowWhenRotateRevokedCredential() {
      var credential = CredentialFixtures.activeUKeyCredential("user-1");
      credential.revoke(UserNo.of("admin-1"));

      assertThatThrownBy(() -> credential.rotate("new-serial-002", UserNo.of("user-1")))
        .isInstanceOf(DomainException.class);
    }
  }
}
