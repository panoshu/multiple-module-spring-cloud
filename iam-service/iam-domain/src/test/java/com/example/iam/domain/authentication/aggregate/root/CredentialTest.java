package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.CredentialStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.strategy.CredentialValidator;
import com.example.iam.types.CredentialId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Credential 聚合根行为")
class CredentialTest {

  private static final UserNo OPERATOR = UserNo.of("U-ADMIN");
  private static final CredentialId CRED_ID = CredentialId.of(2001L);
  private static final Long OWNER_ID = 1001L;
  private static final String OWNER_TYPE = "INTERNET_USER";

  @Test
  @DisplayName("create 工厂方法初始化 ACTIVE 状态")
  void create_initializesActiveStatus() {
    Credential credential = Credential.create(
        CRED_ID, OWNER_TYPE, OWNER_ID,
        CredentialType.PASSWORD,
        "$2a$10$abc", null, Map.of(),
        null, OPERATOR
    );

    assertThat(credential.id()).isEqualTo(CRED_ID);
    assertThat(credential.ownerType()).isEqualTo(OWNER_TYPE);
    assertThat(credential.ownerId()).isEqualTo(OWNER_ID);
    assertThat(credential.credentialType()).isEqualTo(CredentialType.PASSWORD);
    assertThat(credential.secretHash()).isEqualTo("$2a$10$abc");
    assertThat(credential.status()).isEqualTo(CredentialStatus.ACTIVE);
    assertThat(credential.expireTime()).isNull();
  }

  @Test
  @DisplayName("create 拒绝空 ownerType")
  void create_rejectsBlankOwnerType() {
    assertThatThrownBy(() -> Credential.create(
        CRED_ID, "", OWNER_ID,
        CredentialType.PASSWORD, "hash", null, Map.of(), null, OPERATOR
    )).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("create 拒绝空 ownerId")
  void create_rejectsNullOwnerId() {
    assertThatThrownBy(() -> Credential.create(
        CRED_ID, OWNER_TYPE, null,
        CredentialType.PASSWORD, "hash", null, Map.of(), null, OPERATOR
    )).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("create 拒绝空 secretHash")
  void create_rejectsBlankSecretHash() {
    assertThatThrownBy(() -> Credential.create(
        CRED_ID, OWNER_TYPE, OWNER_ID,
        CredentialType.PASSWORD, "", null, Map.of(), null, OPERATOR
    )).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("create 拒绝空凭据类型")
  void create_rejectsNullCredentialType() {
    assertThatThrownBy(() -> Credential.create(
        CRED_ID, OWNER_TYPE, OWNER_ID,
        null, "hash", null, Map.of(), null, OPERATOR
    )).isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("verify 委托给策略并返回验证结果(成功)")
  void verify_delegatesToValidatorAndReturnsTrue() {
    Credential credential = createPasswordCredential();
    CredentialValidator validator = new AlwaysTrueValidator(CredentialType.PASSWORD);

    boolean result = credential.verify("plain-password", validator);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("verify 委托给策略并返回验证结果(失败)")
  void verify_delegatesToValidatorAndReturnsFalse() {
    Credential credential = createPasswordCredential();
    CredentialValidator validator = new AlwaysFalseValidator(CredentialType.PASSWORD);

    boolean result = credential.verify("wrong-password", validator);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("verify 在 EXPIRED 状态时抛 DomainException")
  void verify_throwsWhenExpired() {
    Credential credential = createPasswordCredential();
    credential.markExpired();
    CredentialValidator validator = new AlwaysTrueValidator(CredentialType.PASSWORD);

    assertThatThrownBy(() -> credential.verify("plain", validator))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("verify 在 REVOKED 状态时抛 DomainException")
  void verify_throwsWhenRevoked() {
    Credential credential = createPasswordCredential();
    credential.markRevoked(OPERATOR);
    CredentialValidator validator = new AlwaysTrueValidator(CredentialType.PASSWORD);

    assertThatThrownBy(() -> credential.verify("plain", validator))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("verify 拒绝类型不匹配的验证器")
  void verify_rejectsMismatchedValidatorType() {
    Credential credential = createPasswordCredential();
    CredentialValidator ukeyValidator = new AlwaysTrueValidator(CredentialType.UKEY);

    assertThatThrownBy(() -> credential.verify("plain", ukeyValidator))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("change 更新 secretHash 并递增版本号")
  void change_updatesSecretHashAndIncrementsVersion() {
    Credential credential = createPasswordCredential();
    Version initialVersion = credential.version();

    credential.change("$2a$10$newhash", null, Map.of(), OPERATOR);

    assertThat(credential.secretHash()).isEqualTo("$2a$10$newhash");
    assertThat(credential.version()).isEqualTo(initialVersion.next());
    assertThat(credential.updatedBy()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("change 在 REVOKED 状态时抛 DomainException")
  void change_throwsWhenRevoked() {
    Credential credential = createPasswordCredential();
    credential.markRevoked(OPERATOR);

    assertThatThrownBy(() -> credential.change("newhash", null, Map.of(), OPERATOR))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("markExpired 将 ACTIVE 状态置为 EXPIRED")
  void markExpired_changesActiveToExpired() {
    Credential credential = createPasswordCredential();

    credential.markExpired();

    assertThat(credential.status()).isEqualTo(CredentialStatus.EXPIRED);
  }

  @Test
  @DisplayName("markExpired 在 REVOKED 状态时抛 DomainException")
  void markExpired_throwsWhenRevoked() {
    Credential credential = createPasswordCredential();
    credential.markRevoked(OPERATOR);

    assertThatThrownBy(credential::markExpired)
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("markRevoked 将 ACTIVE 状态置为 REVOKED(终态)")
  void markRevoked_changesActiveToRevoked() {
    Credential credential = createPasswordCredential();

    credential.markRevoked(OPERATOR);

    assertThat(credential.status()).isEqualTo(CredentialStatus.REVOKED);
  }

  @Test
  @DisplayName("markRevoked 将 EXPIRED 状态置为 REVOKED")
  void markRevoked_changesExpiredToRevoked() {
    Credential credential = createPasswordCredential();
    credential.markExpired();

    credential.markRevoked(OPERATOR);

    assertThat(credential.status()).isEqualTo(CredentialStatus.REVOKED);
  }

  @Test
  @DisplayName("markRevoked 在 REVOKED 状态时抛 DomainException(终态不可恢复)")
  void markRevoked_throwsWhenAlreadyRevoked() {
    Credential credential = createPasswordCredential();
    credential.markRevoked(OPERATOR);

    assertThatThrownBy(() -> credential.markRevoked(OPERATOR))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("isExpired 在 expireTime 未到时返回 false")
  void isExpired_returnsFalseWhenNotYetExpired() {
    LocalDateTime futureExpiry = LocalDateTime.now().plusDays(30);
    Credential credential = Credential.create(
        CRED_ID, OWNER_TYPE, OWNER_ID,
        CredentialType.PASSWORD, "hash", null, Map.of(),
        futureExpiry, OPERATOR
    );

    assertThat(credential.isExpired()).isFalse();
  }

  @Test
  @DisplayName("isExpired 在 expireTime 已过时返回 true")
  void isExpired_returnsTrueWhenExpired() {
    LocalDateTime pastExpiry = LocalDateTime.now().minusDays(1);
    Credential credential = Credential.create(
        CRED_ID, OWNER_TYPE, OWNER_ID,
        CredentialType.PASSWORD, "hash", null, Map.of(),
        pastExpiry, OPERATOR
    );

    assertThat(credential.isExpired()).isTrue();
  }

  @Test
  @DisplayName("isExpired 在 expireTime 为 null 时返回 false(永久凭据)")
  void isExpired_returnsFalseWhenPermanent() {
    Credential credential = createPasswordCredential();

    assertThat(credential.isExpired()).isFalse();
  }

  @Test
  @DisplayName("isActive 在 ACTIVE 状态时返回 true")
  void isActive_returnsTrueWhenActive() {
    Credential credential = createPasswordCredential();
    assertThat(credential.isActive()).isTrue();
  }

  @Test
  @DisplayName("reconstitute 从数据库状态恢复完整聚合")
  void reconstitute_restoresFullAggregate() {
    LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 0);
    LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 26, 14, 30);
    LocalDateTime expireTime = LocalDateTime.of(2027, 1, 1, 0, 0);
    Version version = Version.of(3);

    Credential credential = Credential.reconstitute(
        CRED_ID, OWNER_TYPE, OWNER_ID,
        CredentialType.UKEY, "public-key-hash", "salt-value",
        Map.of("publicKey", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA..."),
        CredentialStatus.ACTIVE, expireTime,
        OPERATOR, OPERATOR, createdAt, updatedAt, version
    );

    assertThat(credential.credentialType()).isEqualTo(CredentialType.UKEY);
    assertThat(credential.salt()).isEqualTo("salt-value");
    assertThat(credential.auxData()).containsEntry("publicKey", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...");
    assertThat(credential.status()).isEqualTo(CredentialStatus.ACTIVE);
    assertThat(credential.expireTime()).isEqualTo(expireTime);
    assertThat(credential.version()).isEqualTo(version);
  }

  private Credential createPasswordCredential() {
    return Credential.create(
        CRED_ID, OWNER_TYPE, OWNER_ID,
        CredentialType.PASSWORD, "$2a$10$abc", null, Map.of(),
        null, OPERATOR
    );
  }

  private static class AlwaysTrueValidator implements CredentialValidator {
    private final CredentialType type;

    AlwaysTrueValidator(CredentialType type) { this.type = type; }

    @Override
    public CredentialType supports() { return type; }

    @Override
    public boolean validate(String plainSecret, Credential credential) { return true; }
  }

  private static class AlwaysFalseValidator implements CredentialValidator {
    private final CredentialType type;

    AlwaysFalseValidator(CredentialType type) { this.type = type; }

    @Override
    public CredentialType supports() { return type; }

    @Override
    public boolean validate(String plainSecret, Credential credential) { return false; }
  }
}
