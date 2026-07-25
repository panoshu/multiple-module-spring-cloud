package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStrategyType;
import com.example.iam.types.BranchUserId;
import com.example.iam.types.InternetUserId;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SecondaryAuthSessionTest {

    @Test
    void initiate_should_return_pending_session() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(30);

        SecondaryAuthSession session = SecondaryAuthSession.initiate(
            SecondaryAuthSessionId.of(1L),
            BranchUserId.of(2L), InternetUserId.of(100L),
            SecondaryAuthStrategyType.CREDENTIAL, expiresAt,
            UserNo.of("U002")
        );

        assertEquals(SecondaryAuthSessionId.of(1L), session.id());
        assertEquals(BranchUserId.of(2L), session.branchUserId());
        assertEquals(InternetUserId.of(100L), session.internetUserId());
        assertEquals(SecondaryAuthStrategyType.CREDENTIAL, session.strategyType());
        assertEquals(SecondaryAuthStatus.PENDING, session.status());
        assertFalse(session.isExpired(now));
        assertTrue(session.isExpired(now.plusMinutes(31)));
    }

    @Test
    void complete_should_mark_session_completed() {
        SecondaryAuthSession session = createPendingSession();
        LocalDateTime now = LocalDateTime.now();

        session.complete(UserNo.of("U002"), now);

        assertEquals(SecondaryAuthStatus.COMPLETED, session.status());
        assertNotNull(session.completedAt());
    }

    @Test
    void complete_should_throw_when_session_expired() {
        SecondaryAuthSession session = createPendingSession();
        LocalDateTime future = LocalDateTime.now().plusMinutes(31);

        assertThrows(IllegalStateException.class, () -> session.complete(UserNo.of("U002"), future));
    }

    @Test
    void revoke_should_mark_session_revoked() {
        SecondaryAuthSession session = createPendingSession();

        session.revoke(UserNo.of("U002"));

        assertEquals(SecondaryAuthStatus.REVOKED, session.status());
    }

    @Test
    void complete_should_throw_when_session_already_completed() {
        SecondaryAuthSession session = createPendingSession();
        session.complete(UserNo.of("U002"), LocalDateTime.now());

        assertThrows(IllegalStateException.class, () -> session.complete(UserNo.of("U002"), LocalDateTime.now()));
    }

    @Test
    void revoke_should_throw_when_session_already_completed() {
        SecondaryAuthSession session = createPendingSession();
        session.complete(UserNo.of("U002"), LocalDateTime.now());

        assertThrows(IllegalStateException.class, () -> session.revoke(UserNo.of("U002")));
    }

    @Test
    void revoke_should_be_idempotent_when_already_revoked() {
        SecondaryAuthSession session = createPendingSession();
        session.revoke(UserNo.of("U002"));

        // 再次 revoke 不应抛异常
        assertDoesNotThrow(() -> session.revoke(UserNo.of("U002")));
        assertEquals(SecondaryAuthStatus.REVOKED, session.status());
    }

    @Test
    void reconstitute_should_rebuild_session_from_persistence() {
        LocalDateTime created = LocalDateTime.of(2026, 7, 25, 10, 0);
        LocalDateTime updated = LocalDateTime.of(2026, 7, 25, 11, 0);
        LocalDateTime expiresAt = LocalDateTime.of(2026, 7, 25, 10, 30);
        LocalDateTime completedAt = LocalDateTime.of(2026, 7, 25, 10, 15);

        SecondaryAuthSession session = SecondaryAuthSession.reconstitute(
            SecondaryAuthSessionId.of(1L),
            BranchUserId.of(2L), InternetUserId.of(100L),
            SecondaryAuthStrategyType.CREDENTIAL, expiresAt,
            SecondaryAuthStatus.COMPLETED, completedAt,
            UserNo.of("U002"), UserNo.of("U002"),
            created, updated, Version.of(2L)
        );

        assertEquals(Version.of(2L), session.version());
        assertEquals(SecondaryAuthStatus.COMPLETED, session.status());
        assertEquals(completedAt, session.completedAt());
    }

    private SecondaryAuthSession createPendingSession() {
        return SecondaryAuthSession.initiate(
            SecondaryAuthSessionId.of(1L),
            BranchUserId.of(2L), InternetUserId.of(100L),
            SecondaryAuthStrategyType.CREDENTIAL,
            LocalDateTime.now().plusMinutes(30),
            UserNo.of("U002")
        );
    }
}
