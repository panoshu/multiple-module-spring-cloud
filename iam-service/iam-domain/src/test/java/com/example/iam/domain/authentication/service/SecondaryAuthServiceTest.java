package com.example.iam.domain.authentication.service;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStrategyType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.domain.authentication.repository.SecondaryAuthSessionRepository;
import com.example.iam.domain.authentication.strategy.CredentialSecondaryAuthStrategy;
import com.example.iam.domain.authentication.strategy.PasswordCredentialValidator;
import com.example.iam.types.BranchUserId;
import com.example.iam.types.CredentialId;
import com.example.iam.types.InternetUserId;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SecondaryAuthService} 领域服务单元测试
 *
 * <p>覆盖二次授权会话的发起、完成、撤销场景，包括凭据匹配/不匹配、
 * 会话不存在、会话过期、会话已完成等边界情况</p>
 *
 * @author iam-service
 * @since 2026/7/25
 */
@DisplayName("SecondaryAuthService 领域服务")
class SecondaryAuthServiceTest {

    private SecondaryAuthSessionRepository sessionRepository;
    private CredentialRepository credentialRepository;
    private PasswordCredentialValidator passwordValidator;
    private CredentialSecondaryAuthStrategy strategy;
    private SecondaryAuthService service;

    private static final Long BRANCH_USER_ID = 2L;
    private static final Long INTERNET_USER_ID = 100L;
    private static final String INTERNET_PASSWORD = "HrPwd123";
    private static final UserNo TELLER = UserNo.of("U-teller");
    private static final UserNo ADMIN = UserNo.of("U-admin");

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SecondaryAuthSessionRepository.class);
        credentialRepository = mock(CredentialRepository.class);
        passwordValidator = new PasswordCredentialValidator();
        strategy = new CredentialSecondaryAuthStrategy();
        service = new SecondaryAuthService(
            sessionRepository, strategy, passwordValidator, credentialRepository
        );
    }

    @Test
    @DisplayName("initiate：创建 PENDING 状态会话并保存")
    void initiate_should_create_pending_session_and_save() {
        SecondaryAuthSessionId sessionId = SecondaryAuthSessionId.of(1L);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

        SecondaryAuthSession session = service.initiate(
            sessionId, BranchUserId.of(BRANCH_USER_ID), InternetUserId.of(INTERNET_USER_ID),
            SecondaryAuthStrategyType.CREDENTIAL, expiresAt, TELLER
        );

        assertThat(session.id()).isEqualTo(sessionId);
        assertThat(session.branchUserId().value()).isEqualTo(BRANCH_USER_ID);
        assertThat(session.internetUserId().value()).isEqualTo(INTERNET_USER_ID);
        assertThat(session.strategyType()).isEqualTo(SecondaryAuthStrategyType.CREDENTIAL);
        assertThat(session.status()).isEqualTo(SecondaryAuthStatus.PENDING);
        assertThat(session.expiresAt()).isEqualTo(expiresAt);
        verify(sessionRepository).save(session);
    }

    @Test
    @DisplayName("complete：凭据匹配返回 true 并标记会话 COMPLETED")
    void complete_should_return_true_when_credential_matches() {
        SecondaryAuthSessionId sessionId = SecondaryAuthSessionId.of(1L);
        SecondaryAuthSession session = pendingSession(sessionId, LocalDateTime.now().plusMinutes(30));
        when(sessionRepository.load(sessionId)).thenReturn(Optional.of(session));
        when(credentialRepository.findByOwner("INTERNET_USER", INTERNET_USER_ID))
            .thenReturn(List.of(passwordCredential(INTERNET_PASSWORD)));

        boolean result = service.complete(sessionId, INTERNET_PASSWORD, TELLER);

        assertThat(result).isTrue();
        assertThat(session.status()).isEqualTo(SecondaryAuthStatus.COMPLETED);
        assertThat(session.completedAt()).isNotNull();
        verify(sessionRepository).save(session);
    }

    @Test
    @DisplayName("complete：会话不存在返回 false")
    void complete_should_return_false_when_session_not_found() {
        SecondaryAuthSessionId sessionId = SecondaryAuthSessionId.of(99L);
        when(sessionRepository.load(sessionId)).thenReturn(Optional.empty());

        boolean result = service.complete(sessionId, INTERNET_PASSWORD, TELLER);

        assertThat(result).isFalse();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("complete：凭据不匹配返回 false，会话保持 PENDING")
    void complete_should_return_false_when_credential_mismatch() {
        SecondaryAuthSessionId sessionId = SecondaryAuthSessionId.of(1L);
        SecondaryAuthSession session = pendingSession(sessionId, LocalDateTime.now().plusMinutes(30));
        when(sessionRepository.load(sessionId)).thenReturn(Optional.of(session));
        when(credentialRepository.findByOwner("INTERNET_USER", INTERNET_USER_ID))
            .thenReturn(List.of(passwordCredential(INTERNET_PASSWORD)));

        boolean result = service.complete(sessionId, "WrongPassword", TELLER);

        assertThat(result).isFalse();
        assertThat(session.status()).isEqualTo(SecondaryAuthStatus.PENDING);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("complete：会话已过期返回 false")
    void complete_should_return_false_when_session_expired() {
        SecondaryAuthSessionId sessionId = SecondaryAuthSessionId.of(1L);
        SecondaryAuthSession session = pendingSession(sessionId, LocalDateTime.now().minusMinutes(1));
        when(sessionRepository.load(sessionId)).thenReturn(Optional.of(session));
        when(credentialRepository.findByOwner("INTERNET_USER", INTERNET_USER_ID))
            .thenReturn(List.of(passwordCredential(INTERNET_PASSWORD)));

        boolean result = service.complete(sessionId, INTERNET_PASSWORD, TELLER);

        assertThat(result).isFalse();
        assertThat(session.status()).isEqualTo(SecondaryAuthStatus.PENDING);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("complete：会话已完成返回 false（幂等保护）")
    void complete_should_return_false_when_session_already_completed() {
        SecondaryAuthSessionId sessionId = SecondaryAuthSessionId.of(1L);
        SecondaryAuthSession session = pendingSession(sessionId, LocalDateTime.now().plusMinutes(30));
        session.complete(TELLER, LocalDateTime.now());
        when(sessionRepository.load(sessionId)).thenReturn(Optional.of(session));
        when(credentialRepository.findByOwner("INTERNET_USER", INTERNET_USER_ID))
            .thenReturn(List.of(passwordCredential(INTERNET_PASSWORD)));

        boolean result = service.complete(sessionId, INTERNET_PASSWORD, TELLER);

        assertThat(result).isFalse();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("complete：目标用户无凭据返回 false")
    void complete_should_return_false_when_no_credentials() {
        SecondaryAuthSessionId sessionId = SecondaryAuthSessionId.of(1L);
        SecondaryAuthSession session = pendingSession(sessionId, LocalDateTime.now().plusMinutes(30));
        when(sessionRepository.load(sessionId)).thenReturn(Optional.of(session));
        when(credentialRepository.findByOwner("INTERNET_USER", INTERNET_USER_ID))
            .thenReturn(List.of());

        boolean result = service.complete(sessionId, INTERNET_PASSWORD, TELLER);

        assertThat(result).isFalse();
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("revoke：撤销 PENDING 会话并保存")
    void revoke_should_mark_session_revoked_and_save() {
        SecondaryAuthSessionId sessionId = SecondaryAuthSessionId.of(1L);
        SecondaryAuthSession session = pendingSession(sessionId, LocalDateTime.now().plusMinutes(30));
        when(sessionRepository.load(sessionId)).thenReturn(Optional.of(session));

        service.revoke(sessionId, ADMIN);

        assertThat(session.status()).isEqualTo(SecondaryAuthStatus.REVOKED);
        verify(sessionRepository).save(session);
    }

    @Test
    @DisplayName("revoke：会话不存在抛 IllegalStateException")
    void revoke_should_throw_when_session_not_found() {
        SecondaryAuthSessionId sessionId = SecondaryAuthSessionId.of(99L);
        when(sessionRepository.load(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(sessionId, ADMIN))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("二次授权会话不存在");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("revoke：已撤销会话幂等返回不抛异常")
    void revoke_should_be_idempotent_when_already_revoked() {
        SecondaryAuthSessionId sessionId = SecondaryAuthSessionId.of(1L);
        SecondaryAuthSession session = pendingSession(sessionId, LocalDateTime.now().plusMinutes(30));
        session.revoke(ADMIN);
        when(sessionRepository.load(sessionId)).thenReturn(Optional.of(session));

        service.revoke(sessionId, ADMIN);

        assertThat(session.status()).isEqualTo(SecondaryAuthStatus.REVOKED);
        verify(sessionRepository).save(session);
    }

    private SecondaryAuthSession pendingSession(SecondaryAuthSessionId id, LocalDateTime expiresAt) {
        return SecondaryAuthSession.reconstitute(
            id,
            BranchUserId.of(BRANCH_USER_ID), InternetUserId.of(INTERNET_USER_ID),
            SecondaryAuthStrategyType.CREDENTIAL, expiresAt,
            SecondaryAuthStatus.PENDING, null,
            TELLER, TELLER,
            LocalDateTime.now(), LocalDateTime.now(), Version.initial()
        );
    }

    private Credential passwordCredential(String plainPassword) {
        String hash = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
        return Credential.reconstitute(
            CredentialId.of(10L),
            "INTERNET_USER", INTERNET_USER_ID,
            CredentialType.PASSWORD, hash, null,
            UserStatus.ACTIVE, LocalDateTime.now(),
            UserNo.of("U-creator"), UserNo.of("U-creator"),
            LocalDateTime.now(), LocalDateTime.now(),
            Version.initial()
        );
    }
}
