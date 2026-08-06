package com.pension.permission.domain.credential.aggregate;

import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.credential.enumeration.CredentialStatus;
import com.pension.permission.domain.credential.enumeration.CredentialType;
import com.pension.permission.domain.credential.event.PasswordChanged;
import com.pension.permission.domain.fixture.CredentialFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PasswordCredential 聚合根测试")
class PasswordCredentialTest {

  @Nested
  @DisplayName("创建 create")
  class CreateTest {

    @Test
    @DisplayName("应创建 ACTIVE 状态的密码凭证")
    void shouldCreateActiveCredential() {
      var credential = CredentialFixtures.activePasswordCredential("user-1");

      assertThat(credential.status()).isEqualTo(CredentialStatus.ACTIVE);
      assertThat(credential.type()).isEqualTo(CredentialType.PASSWORD);
      assertThat(credential.passwordHash()).isEqualTo("hashed-password-001");
    }

    @Test
    @DisplayName("密码哈希为空应抛 DomainException")
    void shouldThrowWhenPasswordHashBlank() {
      assertThatThrownBy(() -> PasswordCredential.create(
        new com.pension.permission.types.CredentialId("c-pwd-x"),
        UserNo.of("user-1"),
        "  ",
        java.util.Set.of(),
        UserNo.of("creator-1"),
        CredentialFixtures.fixedClock("2026-01-01T00:00:00Z")))
        .isInstanceOf(DomainException.class);
    }
  }

  @Nested
  @DisplayName("轮换密码 rotatePassword")
  class RotatePasswordTest {

    @Test
    @DisplayName("应更新密码哈希并注册事件")
    void shouldRotatePassword() {
      var credential = CredentialFixtures.activePasswordCredential("user-1");

      credential.rotatePassword("new-hash-002", UserNo.of("user-1"),
        CredentialFixtures.fixedClock("2026-01-02T00:00:00Z"));

      assertThat(credential.passwordHash()).isEqualTo("new-hash-002");
      assertThat(credential.domainEvents()).anyMatch(e -> e instanceof PasswordChanged);
    }

    @Test
    @DisplayName("新旧密码相同应抛 DomainException")
    void shouldThrowWhenSameAsOld() {
      var credential = CredentialFixtures.activePasswordCredential("user-1");

      assertThatThrownBy(() -> credential.rotatePassword("hashed-password-001", UserNo.of("user-1"),
        CredentialFixtures.fixedClock("2026-01-02T00:00:00Z")))
        .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("撤销后轮换密码应抛 DomainException")
    void shouldThrowWhenRotateRevokedCredential() {
      var credential = CredentialFixtures.activePasswordCredential("user-1");
      credential.revoke(UserNo.of("admin-1"));

      assertThatThrownBy(() -> credential.rotatePassword("new-hash-002", UserNo.of("user-1"),
        CredentialFixtures.fixedClock("2026-01-02T00:00:00Z")))
        .isInstanceOf(DomainException.class);
    }
  }
}
