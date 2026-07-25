package com.example.iam.domain.authentication.service;

import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStrategyType;
import com.example.iam.domain.authentication.repository.SecondaryAuthSessionRepository;
import com.example.iam.types.BranchUserId;
import com.example.iam.types.InternetUserId;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link IdentitySwitchService} 领域服务单元测试
 *
 * <p>覆盖网点柜员身份切换的查询场景：有效代理会话存在/不存在/已过期/仅 PENDING 等</p>
 *
 * @author iam-service
 * @since 2026/7/25
 */
@DisplayName("IdentitySwitchService 领域服务")
class IdentitySwitchServiceTest {

    private SecondaryAuthSessionRepository sessionRepository;
    private IdentitySwitchService service;

    private static final Long BRANCH_USER_ID = 2L;
    private static final Long INTERNET_USER_ID = 100L;
    private static final Long ANOTHER_INTERNET_USER_ID = 101L;
    private static final UserNo TELLER = UserNo.of("U-teller");

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SecondaryAuthSessionRepository.class);
        service = new IdentitySwitchService(sessionRepository);
    }

    @Test
    @DisplayName("getCurrentActingAs：存在 COMPLETED 且未过期的会话返回 internetUserId")
    void getCurrentActingAs_should_return_internet_user_id_when_active_completed_session_exists() {
        SecondaryAuthSession completed = completedSession(
            1L, BRANCH_USER_ID, INTERNET_USER_ID, LocalDateTime.now().plusMinutes(30)
        );
        when(sessionRepository.findActiveByBranchUser(BranchUserId.of(BRANCH_USER_ID)))
            .thenReturn(List.of(completed));

        Optional<InternetUserId> result = service.getCurrentActingAs(BranchUserId.of(BRANCH_USER_ID));

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo(INTERNET_USER_ID);
    }

    @Test
    @DisplayName("getCurrentActingAs：无任何会话返回 empty")
    void getCurrentActingAs_should_return_empty_when_no_active_session() {
        when(sessionRepository.findActiveByBranchUser(BranchUserId.of(BRANCH_USER_ID)))
            .thenReturn(List.of());

        Optional<InternetUserId> result = service.getCurrentActingAs(BranchUserId.of(BRANCH_USER_ID));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCurrentActingAs：会话已过期返回 empty")
    void getCurrentActingAs_should_return_empty_when_session_expired() {
        SecondaryAuthSession expiredCompleted = completedSession(
            1L, BRANCH_USER_ID, INTERNET_USER_ID, LocalDateTime.now().minusMinutes(1)
        );
        when(sessionRepository.findActiveByBranchUser(BranchUserId.of(BRANCH_USER_ID)))
            .thenReturn(List.of(expiredCompleted));

        Optional<InternetUserId> result = service.getCurrentActingAs(BranchUserId.of(BRANCH_USER_ID));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCurrentActingAs：仅 PENDING 会话（未完成）返回 empty")
    void getCurrentActingAs_should_return_empty_when_session_pending() {
        SecondaryAuthSession pending = pendingSession(
            1L, BRANCH_USER_ID, INTERNET_USER_ID, LocalDateTime.now().plusMinutes(30)
        );
        when(sessionRepository.findActiveByBranchUser(BranchUserId.of(BRANCH_USER_ID)))
            .thenReturn(List.of(pending));

        Optional<InternetUserId> result = service.getCurrentActingAs(BranchUserId.of(BRANCH_USER_ID));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getCurrentActingAs：多个 COMPLETED 会话时返回最近完成的一个")
    void getCurrentActingAs_should_return_latest_when_multiple_completed_sessions() {
        SecondaryAuthSession older = completedSession(
            1L, BRANCH_USER_ID, INTERNET_USER_ID,
            LocalDateTime.now().plusMinutes(30),
            LocalDateTime.now().minusMinutes(20)
        );
        SecondaryAuthSession newer = completedSession(
            2L, BRANCH_USER_ID, ANOTHER_INTERNET_USER_ID,
            LocalDateTime.now().plusMinutes(30),
            LocalDateTime.now().minusMinutes(5)
        );
        when(sessionRepository.findActiveByBranchUser(BranchUserId.of(BRANCH_USER_ID)))
            .thenReturn(List.of(older, newer));

        Optional<InternetUserId> result = service.getCurrentActingAs(BranchUserId.of(BRANCH_USER_ID));

        assertThat(result).isPresent();
        assertThat(result.get().value()).isEqualTo(ANOTHER_INTERNET_USER_ID);
    }

    @Test
    @DisplayName("canSwitchBack：有代理身份返回 true")
    void canSwitchBack_should_return_true_when_acting_as_present() {
        SecondaryAuthSession completed = completedSession(
            1L, BRANCH_USER_ID, INTERNET_USER_ID, LocalDateTime.now().plusMinutes(30)
        );
        when(sessionRepository.findActiveByBranchUser(BranchUserId.of(BRANCH_USER_ID)))
            .thenReturn(List.of(completed));

        boolean result = service.canSwitchBack(BranchUserId.of(BRANCH_USER_ID));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canSwitchBack：无代理身份返回 false")
    void canSwitchBack_should_return_false_when_no_acting_as() {
        when(sessionRepository.findActiveByBranchUser(BranchUserId.of(BRANCH_USER_ID)))
            .thenReturn(List.of());

        boolean result = service.canSwitchBack(BranchUserId.of(BRANCH_USER_ID));

        assertThat(result).isFalse();
    }

    private SecondaryAuthSession pendingSession(Long sessionId, Long branchUserId, Long internetUserId,
                                                LocalDateTime expiresAt) {
        return SecondaryAuthSession.reconstitute(
            SecondaryAuthSessionId.of(sessionId),
            BranchUserId.of(branchUserId), InternetUserId.of(internetUserId),
            SecondaryAuthStrategyType.CREDENTIAL, expiresAt,
            SecondaryAuthStatus.PENDING, null,
            TELLER, TELLER,
            LocalDateTime.now(), LocalDateTime.now(), Version.initial()
        );
    }

    private SecondaryAuthSession completedSession(Long sessionId, Long branchUserId, Long internetUserId,
                                                  LocalDateTime expiresAt) {
        return completedSession(sessionId, branchUserId, internetUserId, expiresAt, LocalDateTime.now());
    }

    private SecondaryAuthSession completedSession(Long sessionId, Long branchUserId, Long internetUserId,
                                                  LocalDateTime expiresAt, LocalDateTime completedAt) {
        return SecondaryAuthSession.reconstitute(
            SecondaryAuthSessionId.of(sessionId),
            BranchUserId.of(branchUserId), InternetUserId.of(internetUserId),
            SecondaryAuthStrategyType.CREDENTIAL, expiresAt,
            SecondaryAuthStatus.COMPLETED, completedAt,
            TELLER, TELLER,
            LocalDateTime.now(), LocalDateTime.now(), Version.initial()
        );
    }
}
