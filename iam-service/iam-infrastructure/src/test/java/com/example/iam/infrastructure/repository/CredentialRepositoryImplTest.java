package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.infrastructure.IamInfrastructureTestApplication;
import com.example.iam.types.CredentialId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CredentialRepositoryImpl} 集成测试。
 *
 * <p>验证凭据聚合根的 CRUD、按归属方查询活动凭据、按归属方查询所有凭据等场景。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("CredentialRepositoryImpl 集成测试")
@SpringBootTest(classes = IamInfrastructureTestApplication.class)
@ActiveProfiles("test")
@Transactional
class CredentialRepositoryImplTest {

    @Autowired
    private CredentialRepository credentialRepository;

    private static final Long CREDENTIAL_ID_VALUE = 20001L;
    private static final Long ALT_CREDENTIAL_ID_VALUE = 20002L;
    private static final Long OWNER_ID = 10001L;
    private static final String OWNER_TYPE = "INTERNET_USER";
    private static final CredentialType CREDENTIAL_TYPE = CredentialType.PASSWORD;
    private static final String SECRET_HASH = "$2a$10$hashedSecretValue";
    private static final String SALT = "random-salt";
    private static final UserNo OPERATOR = UserNo.of("U-ADMIN-001");

    @Nested
    @DisplayName("save + load: 新建与读取")
    class SaveAndLoadTest {

        @Test
        @DisplayName("新建凭据后能通过 ID 加载,关键字段一致")
        void shouldSaveNewCredentialAndLoadById() {
            Credential credential = Credential.create(
                    CredentialId.of(CREDENTIAL_ID_VALUE), OWNER_TYPE, OWNER_ID,
                    CREDENTIAL_TYPE, SECRET_HASH, SALT, Map.of("k", "v"),
                    null, OPERATOR);

            credentialRepository.save(credential);

            Optional<Credential> loaded = credentialRepository.load(CredentialId.of(CREDENTIAL_ID_VALUE));

            assertThat(loaded).isPresent();
            Credential actual = loaded.get();
            assertThat(actual.id().value()).isEqualTo(CREDENTIAL_ID_VALUE);
            assertThat(actual.ownerType()).isEqualTo(OWNER_TYPE);
            assertThat(actual.ownerId()).isEqualTo(OWNER_ID);
            assertThat(actual.credentialType()).isEqualTo(CREDENTIAL_TYPE);
            assertThat(actual.secretHash()).isEqualTo(SECRET_HASH);
            assertThat(actual.salt()).isEqualTo(SALT);
            assertThat(actual.auxData()).containsEntry("k", "v");
            assertThat(actual.status()).isEqualTo(CredentialStatus.ACTIVE);
            assertThat(actual.createdBy()).isEqualTo(OPERATOR);
        }

        @Test
        @DisplayName("load 不存在的 ID 返回 empty")
        void shouldReturnEmptyWhenLoadNonexistentId() {
            Optional<Credential> loaded = credentialRepository.load(CredentialId.of(999999L));

            assertThat(loaded).isEmpty();
        }

        @Test
        @DisplayName("load 传入 null 返回 empty")
        void shouldReturnEmptyWhenLoadNullId() {
            Optional<Credential> loaded = credentialRepository.load(null);

            assertThat(loaded).isEmpty();
        }
    }

    @Nested
    @DisplayName("findActive: 按归属方查询活动凭据")
    class FindActiveTest {

        @Test
        @DisplayName("按 ownerId + ownerType + credentialType 命中 ACTIVE 凭据")
        void shouldFindActiveCredential() {
            Credential credential = Credential.create(
                    CredentialId.of(CREDENTIAL_ID_VALUE), OWNER_TYPE, OWNER_ID,
                    CREDENTIAL_TYPE, SECRET_HASH, SALT, Map.of(),
                    null, OPERATOR);
            credentialRepository.save(credential);

            Optional<Credential> found = credentialRepository.findActive(
                    OWNER_ID, OWNER_TYPE, CREDENTIAL_TYPE);

            assertThat(found).isPresent();
            assertThat(found.get().ownerId()).isEqualTo(OWNER_ID);
            assertThat(found.get().status()).isEqualTo(CredentialStatus.ACTIVE);
        }

        @Test
        @DisplayName("REVOKED 状态的凭据不被 findActive 返回")
        void shouldNotFindRevokedCredential() {
            Credential credential = Credential.create(
                    CredentialId.of(CREDENTIAL_ID_VALUE), OWNER_TYPE, OWNER_ID,
                    CREDENTIAL_TYPE, SECRET_HASH, SALT, Map.of(),
                    null, OPERATOR);
            credential.markRevoked(OPERATOR);
            credentialRepository.save(credential);

            Optional<Credential> found = credentialRepository.findActive(
                    OWNER_ID, OWNER_TYPE, CREDENTIAL_TYPE);

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByOwner: 按归属方查询所有凭据")
    class FindAllByOwnerTest {

        @Test
        @DisplayName("同一 owner 下返回所有 ACTIVE/EXPIRED 凭据")
        void shouldFindAllByOwner() {
            Credential c1 = Credential.create(
                    CredentialId.of(CREDENTIAL_ID_VALUE), OWNER_TYPE, OWNER_ID,
                    CredentialType.PASSWORD, SECRET_HASH, SALT, Map.of(),
                    null, OPERATOR);
            Credential c2 = Credential.create(
                    CredentialId.of(ALT_CREDENTIAL_ID_VALUE), OWNER_TYPE, OWNER_ID,
                    CredentialType.UKEY, "ukey-hash", null, Map.of(),
                    null, OPERATOR);
            credentialRepository.save(c1);
            credentialRepository.save(c2);

            List<Credential> credentials = credentialRepository.findAllByOwner(OWNER_ID, OWNER_TYPE);

            assertThat(credentials).hasSize(2);
            assertThat(credentials).extracting(c -> c.credentialType())
                    .containsExactlyInAnyOrder(CredentialType.PASSWORD, CredentialType.UKEY);
        }

        @Test
        @DisplayName("owner 不存在凭据时返回空列表")
        void shouldReturnEmptyListForUnknownOwner() {
            List<Credential> credentials = credentialRepository.findAllByOwner(999999L, OWNER_TYPE);

            assertThat(credentials).isEmpty();
        }
    }

    @Nested
    @DisplayName("delete: 软删除")
    class DeleteTest {

        @Test
        @DisplayName("delete 后 load 返回 empty(软删除生效)")
        void shouldSoftDeleteCredential() {
            Credential credential = Credential.create(
                    CredentialId.of(CREDENTIAL_ID_VALUE), OWNER_TYPE, OWNER_ID,
                    CREDENTIAL_TYPE, SECRET_HASH, SALT, Map.of(),
                    null, OPERATOR);
            credentialRepository.save(credential);

            credentialRepository.delete(credential);

            assertThat(credentialRepository.load(CredentialId.of(CREDENTIAL_ID_VALUE))).isEmpty();
        }

        @Test
        @DisplayName("deleteById 后 load 返回 empty")
        void shouldSoftDeleteById() {
            Credential credential = Credential.create(
                    CredentialId.of(CREDENTIAL_ID_VALUE), OWNER_TYPE, OWNER_ID,
                    CREDENTIAL_TYPE, SECRET_HASH, SALT, Map.of(),
                    null, OPERATOR);
            credentialRepository.save(credential);

            credentialRepository.deleteById(CredentialId.of(CREDENTIAL_ID_VALUE));

            assertThat(credentialRepository.load(CredentialId.of(CREDENTIAL_ID_VALUE))).isEmpty();
        }

        @Test
        @DisplayName("delete null 凭据不抛异常")
        void shouldNotThrowWhenDeleteNull() {
            credentialRepository.delete(null);
            credentialRepository.deleteById(null);
        }
    }
}
