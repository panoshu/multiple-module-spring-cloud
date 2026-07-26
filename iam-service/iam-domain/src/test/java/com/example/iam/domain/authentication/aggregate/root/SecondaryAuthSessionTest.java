package com.example.iam.domain.authentication.aggregate.root;

import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionSnapshot;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.iam.types.UserId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SecondaryAuthSession 聚合根行为测试。
 *
 * <p>覆盖:
 * <ul>
 *   <li>工厂方法 {@code initiate} 创建 PENDING 会话</li>
 *   <li>状态机转换:PENDING → AUTHORIZED/REJECTED,AUTHORIZED → EXPIRED/REVOKED/CLOSED</li>
 *   <li>权限快照冻结</li>
 *   <li>生效判断 {@code isEffectiveAt} 与操作员匹配 {@code authorizes}</li>
 *   <li>非法状态转换抛 DomainException</li>
 *   <li>{@code reconstitute} 数据库重建</li>
 * </ul>
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("SecondaryAuthSession 聚合根行为")
class SecondaryAuthSessionTest {

  private static final UserNo TELLER = UserNo.of("U-TELLER");
  private static final UserNo APPROVER = UserNo.of("U-APPROVER");
  private static final Long TELLER_ID = 1001L;
  private static final Long APPROVER_ID = 2001L;
  private static final String CUSTOMER_NO = "CUST-001";
  private static final String PLAN_ID = "PLAN-001";
  private static final SecondaryAuthSessionId SESSION_ID = SecondaryAuthSessionId.of(9001L);

  @Test
  @DisplayName("initiate 工厂方法初始化 PENDING 状态")
  void initiate_initializesPendingStatus() {
    LocalDateTime before = LocalDateTime.now();

    SecondaryAuthSession session = SecondaryAuthSession.initiate(
        SESSION_ID, TELLER_ID, APPROVER_ID, CUSTOMER_NO, PLAN_ID, TELLER
    );

    assertThat(session.id()).isEqualTo(SESSION_ID);
    assertThat(session.tellerId()).isEqualTo(TELLER_ID);
    assertThat(session.approverId()).isEqualTo(APPROVER_ID);
    assertThat(session.customerNo()).isEqualTo(CUSTOMER_NO);
    assertThat(session.planId()).isEqualTo(PLAN_ID);
    assertThat(session.status()).isEqualTo(SecondaryAuthStatus.PENDING);
    assertThat(session.initiatedAt()).isAfterOrEqualTo(before);
    assertThat(session.authorizedAt()).isNull();
    assertThat(session.expireAt()).isNull();
    assertThat(session.permissionSnapshot()).isNull();
    assertThat(session.revokeReason()).isNull();
  }

  @Test
  @DisplayName("initiate 拒绝空 customerNo")
  void initiate_rejectsBlankCustomerNo() {
    assertThatThrownBy(() -> SecondaryAuthSession.initiate(
        SESSION_ID, TELLER_ID, APPROVER_ID, "", PLAN_ID, TELLER
    )).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("initiate 拒绝空 planId")
  void initiate_rejectsBlankPlanId() {
    assertThatThrownBy(() -> SecondaryAuthSession.initiate(
        SESSION_ID, TELLER_ID, APPROVER_ID, CUSTOMER_NO, "", TELLER
    )).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("authorize 冻结权限快照并转入 AUTHORIZED")
  void authorize_freezesSnapshotAndTransitionsToAuthorized() {
    SecondaryAuthSession session = createPendingSession();
    LocalDateTime expireAt = LocalDateTime.now().plusHours(2);
    PermissionSnapshot snapshot = buildSnapshot(APPROVER_ID);

    session.authorize(snapshot, expireAt, APPROVER);

    assertThat(session.status()).isEqualTo(SecondaryAuthStatus.AUTHORIZED);
    assertThat(session.permissionSnapshot()).containsExactlyInAnyOrderElementsOf(snapshot.permissions());
    assertThat(session.authorizedAt()).isNotNull();
    assertThat(session.expireAt()).isEqualTo(expireAt);
    assertThat(session.updatedBy()).isEqualTo(APPROVER);
  }

  @Test
  @DisplayName("authorize 在 AUTHORIZED 状态时抛 DomainException")
  void authorize_throwsWhenAlreadyAuthorized() {
    SecondaryAuthSession session = createAuthorizedSession();
    LocalDateTime newExpireAt = LocalDateTime.now().plusHours(4);
    PermissionSnapshot snapshot = buildSnapshot(APPROVER_ID);

    assertThatThrownBy(() -> session.authorize(snapshot, newExpireAt, APPROVER))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("authorize 在 REVOKED 状态时抛 DomainException")
  void authorize_throwsWhenRevoked() {
    SecondaryAuthSession session = createAuthorizedSession();
    session.revoke(APPROVER, "撤销原因");

    PermissionSnapshot snapshot = buildSnapshot(APPROVER_ID);

    assertThatThrownBy(() -> session.authorize(snapshot, LocalDateTime.now().plusHours(2), APPROVER))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("reject 在 PENDING 状态时转入 REJECTED")
  void reject_transitionsPendingToRejected() {
    SecondaryAuthSession session = createPendingSession();

    session.reject(APPROVER);

    assertThat(session.status()).isEqualTo(SecondaryAuthStatus.REJECTED);
    assertThat(session.updatedBy()).isEqualTo(APPROVER);
  }

  @Test
  @DisplayName("reject 在 AUTHORIZED 状态时抛 DomainException")
  void reject_throwsWhenAlreadyAuthorized() {
    SecondaryAuthSession session = createAuthorizedSession();

    assertThatThrownBy(() -> session.reject(APPROVER))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("revoke 在 AUTHORIZED 状态时转入 REVOKED 并记录原因")
  void revoke_recordsReasonAndTransitionsToRevoked() {
    SecondaryAuthSession session = createAuthorizedSession();

    session.revoke(APPROVER, "柜员操作异常");

    assertThat(session.status()).isEqualTo(SecondaryAuthStatus.REVOKED);
    assertThat(session.revokeReason()).isEqualTo("柜员操作异常");
    assertThat(session.updatedBy()).isEqualTo(APPROVER);
  }

  @Test
  @DisplayName("revoke 拒绝空原因")
  void revoke_rejectsBlankReason() {
    SecondaryAuthSession session = createAuthorizedSession();

    assertThatThrownBy(() -> session.revoke(APPROVER, ""))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("revoke 在 PENDING 状态时抛 DomainException")
  void revoke_throwsWhenPending() {
    SecondaryAuthSession session = createPendingSession();

    assertThatThrownBy(() -> session.revoke(APPROVER, "原因"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("revoke 在 REVOKED 终态时抛 DomainException")
  void revoke_throwsWhenAlreadyRevoked() {
    SecondaryAuthSession session = createAuthorizedSession();
    session.revoke(APPROVER, "首次撤销");

    assertThatThrownBy(() -> session.revoke(APPROVER, "再次撤销"))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("markExpired 在 AUTHORIZED 状态时转入 EXPIRED")
  void markExpired_transitionsAuthorizedToExpired() {
    SecondaryAuthSession session = createAuthorizedSession();

    session.markExpired();

    assertThat(session.status()).isEqualTo(SecondaryAuthStatus.EXPIRED);
  }

  @Test
  @DisplayName("markExpired 在 PENDING 状态时抛 DomainException")
  void markExpired_throwsWhenPending() {
    SecondaryAuthSession session = createPendingSession();

    assertThatThrownBy(session::markExpired)
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("close 在 AUTHORIZED 状态时转入 CLOSED")
  void close_transitionsAuthorizedToClosed() {
    SecondaryAuthSession session = createAuthorizedSession();

    session.close(TELLER);

    assertThat(session.status()).isEqualTo(SecondaryAuthStatus.CLOSED);
    assertThat(session.updatedBy()).isEqualTo(TELLER);
  }

  @Test
  @DisplayName("close 在 PENDING 状态时抛 DomainException")
  void close_throwsWhenPending() {
    SecondaryAuthSession session = createPendingSession();

    assertThatThrownBy(() -> session.close(TELLER))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("isEffectiveAt 在 AUTHORIZED 且时间窗口内返回 true")
  void isEffectiveAt_returnsTrueWhenAuthorizedAndWithinWindow() {
    LocalDateTime authorizedAt = LocalDateTime.now().minusMinutes(30);
    LocalDateTime expireAt = LocalDateTime.now().plusMinutes(30);
    SecondaryAuthSession session = createAuthorizedSessionAt(authorizedAt, expireAt);

    assertThat(session.isEffectiveAt(LocalDateTime.now())).isTrue();
  }

  @Test
  @DisplayName("isEffectiveAt 在过期时间之后返回 false")
  void isEffectiveAt_returnsFalseAfterExpiry() {
    LocalDateTime authorizedAt = LocalDateTime.now().minusHours(2);
    LocalDateTime expireAt = LocalDateTime.now().minusHours(1);
    SecondaryAuthSession session = createAuthorizedSessionAt(authorizedAt, expireAt);

    assertThat(session.isEffectiveAt(LocalDateTime.now())).isFalse();
  }

  @Test
  @DisplayName("isEffectiveAt 在授权时间之前返回 false")
  void isEffectiveAt_returnsFalseBeforeAuthorization() {
    LocalDateTime authorizedAt = LocalDateTime.now().plusHours(1);
    LocalDateTime expireAt = LocalDateTime.now().plusHours(2);
    SecondaryAuthSession session = createAuthorizedSessionAt(authorizedAt, expireAt);

    assertThat(session.isEffectiveAt(LocalDateTime.now())).isFalse();
  }

  @Test
  @DisplayName("isEffectiveAt 在 PENDING 状态时返回 false")
  void isEffectiveAt_returnsFalseWhenPending() {
    SecondaryAuthSession session = createPendingSession();

    assertThat(session.isEffectiveAt(LocalDateTime.now())).isFalse();
  }

  @Test
  @DisplayName("isEffectiveAt 在 REVOKED 状态时返回 false")
  void isEffectiveAt_returnsFalseWhenRevoked() {
    SecondaryAuthSession session = createAuthorizedSession();
    session.revoke(APPROVER, "撤销");

    assertThat(session.isEffectiveAt(LocalDateTime.now())).isFalse();
  }

  @Test
  @DisplayName("authorizes 在 AUTHORIZED 状态且柜员ID匹配时返回 true")
  void authorizes_returnsTrueWhenAuthorizedAndTellerMatches() {
    SecondaryAuthSession session = createAuthorizedSession();

    assertThat(session.authorizes(TELLER_ID)).isTrue();
  }

  @Test
  @DisplayName("authorizes 在柜员ID不匹配时返回 false")
  void authorizes_returnsFalseWhenTellerMismatch() {
    SecondaryAuthSession session = createAuthorizedSession();

    assertThat(session.authorizes(9999L)).isFalse();
  }

  @Test
  @DisplayName("authorizes 在 PENDING 状态时返回 false")
  void authorizes_returnsFalseWhenPending() {
    SecondaryAuthSession session = createPendingSession();

    assertThat(session.authorizes(TELLER_ID)).isFalse();
  }

  @Test
  @DisplayName("reconstitute 从数据库状态恢复完整聚合")
  void reconstitute_restoresFullAggregate() {
    LocalDateTime initiatedAt = LocalDateTime.of(2026, 7, 26, 9, 0);
    LocalDateTime authorizedAt = LocalDateTime.of(2026, 7, 26, 9, 15);
    LocalDateTime expireAt = LocalDateTime.of(2026, 7, 26, 11, 15);
    Set<PermissionCode> snapshot = Set.of(
        PermissionCode.of("business1.handle"),
        PermissionCode.of("business2.query")
    );
    Version version = Version.of(2);

    SecondaryAuthSession session = SecondaryAuthSession.reconstitute(
        SESSION_ID, TELLER_ID, APPROVER_ID, CUSTOMER_NO, PLAN_ID,
        snapshot, SecondaryAuthStatus.AUTHORIZED,
        initiatedAt, authorizedAt, expireAt, null,
        TELLER, APPROVER,
        initiatedAt, authorizedAt, version
    );

    assertThat(session.tellerId()).isEqualTo(TELLER_ID);
    assertThat(session.approverId()).isEqualTo(APPROVER_ID);
    assertThat(session.customerNo()).isEqualTo(CUSTOMER_NO);
    assertThat(session.planId()).isEqualTo(PLAN_ID);
    assertThat(session.permissionSnapshot()).containsExactlyInAnyOrderElementsOf(snapshot);
    assertThat(session.status()).isEqualTo(SecondaryAuthStatus.AUTHORIZED);
    assertThat(session.initiatedAt()).isEqualTo(initiatedAt);
    assertThat(session.authorizedAt()).isEqualTo(authorizedAt);
    assertThat(session.expireAt()).isEqualTo(expireAt);
    assertThat(session.version()).isEqualTo(version);
  }

  @Test
  @DisplayName("permissionSnapshot 在未授权时为 null")
  void permissionSnapshot_isNullWhenNotAuthorized() {
    SecondaryAuthSession session = createPendingSession();
    assertThat(session.permissionSnapshot()).isNull();
  }

  @Test
  @DisplayName("permissionSnapshot 在授权后不可变")
  void permissionSnapshot_isImmutableAfterAuthorization() {
    SecondaryAuthSession session = createAuthorizedSession();

    Set<PermissionCode> snapshot = session.permissionSnapshot();

    assertThatThrownBy(() -> snapshot.add(PermissionCode.of("business3.handle")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private SecondaryAuthSession createPendingSession() {
    return SecondaryAuthSession.initiate(
        SESSION_ID, TELLER_ID, APPROVER_ID, CUSTOMER_NO, PLAN_ID, TELLER
    );
  }

  private SecondaryAuthSession createAuthorizedSession() {
    SecondaryAuthSession session = createPendingSession();
    LocalDateTime expireAt = LocalDateTime.now().plusHours(2);
    PermissionSnapshot snapshot = buildSnapshot(APPROVER_ID);
    session.authorize(snapshot, expireAt, APPROVER);
    return session;
  }

  private SecondaryAuthSession createAuthorizedSessionAt(LocalDateTime authorizedAt, LocalDateTime expireAt) {
    return SecondaryAuthSession.reconstitute(
        SESSION_ID, TELLER_ID, APPROVER_ID, CUSTOMER_NO, PLAN_ID,
        Set.of(PermissionCode.of("business1.handle")),
        SecondaryAuthStatus.AUTHORIZED,
        authorizedAt.minusMinutes(60), authorizedAt, expireAt, null,
        TELLER, APPROVER,
        authorizedAt.minusMinutes(60), authorizedAt, Version.of(1)
    );
  }

  private static PermissionSnapshot buildSnapshot(Long userId) {
    return new PermissionSnapshot(
        UserId.of(userId),
        PLAN_ID,
        Set.of(
            PermissionCode.of("business1.handle"),
            PermissionCode.of("business2.query")
        ),
        LocalDateTime.now()
    );
  }
}
