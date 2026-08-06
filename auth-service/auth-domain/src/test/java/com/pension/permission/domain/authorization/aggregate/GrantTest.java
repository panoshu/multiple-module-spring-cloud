package com.pension.permission.domain.authorization.aggregate;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.enumeration.GrantStatus;
import com.pension.permission.domain.authorization.event.GrantApproved;
import com.pension.permission.domain.authorization.event.GrantRejected;
import com.pension.permission.domain.authorization.event.GrantRevoked;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.fixture.AuthorizationFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Grant 聚合根测试")
class GrantTest {

  @Nested
  @DisplayName("状态流转")
  class StatusTransitionTest {

    @Test
    @DisplayName("approve 应将 PENDING_APPROVAL 转为 EFFECTIVE 并注册事件")
    void shouldApprovePendingGrant() {
      var grant = AuthorizationFixtures.pendingAllowGrant();
      var approver = UserNo.of("approver-1");

      grant.approve(approver);

      assertThat(grant.status()).isEqualTo(GrantStatus.EFFECTIVE);
      assertThat(grant.domainEvents()).anyMatch(e -> e instanceof GrantApproved);
    }

    @Test
    @DisplayName("reject 应将 PENDING_APPROVAL 转为 REJECTED 并注册事件")
    void shouldRejectPendingGrant() {
      var grant = AuthorizationFixtures.pendingAllowGrant();
      var rejecter = UserNo.of("rejecter-1");

      grant.reject(rejecter);

      assertThat(grant.status()).isEqualTo(GrantStatus.REJECTED);
      assertThat(grant.domainEvents()).anyMatch(e -> e instanceof GrantRejected);
    }

    @Test
    @DisplayName("revoke 应将 EFFECTIVE 转为 REVOKED 并注册事件")
    void shouldRevokeEffectiveGrant() {
      var grant = AuthorizationFixtures.effectiveAllowGrant();
      var revoker = UserNo.of("revoker-1");

      grant.revoke(revoker);

      assertThat(grant.status()).isEqualTo(GrantStatus.REVOKED);
      assertThat(grant.domainEvents()).anyMatch(e -> e instanceof GrantRevoked);
    }

    @Test
    @DisplayName("对非 PENDING_APPROVAL 状态调用 approve 应抛 IllegalStateException")
    void shouldThrowWhenApproveNonPendingGrant() {
      var grant = AuthorizationFixtures.effectiveAllowGrant();
      assertThatThrownBy(() -> grant.approve(UserNo.of("u-1")))
        .isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("权限判定")
  class PermissionCheckTest {

    @Test
    @DisplayName("isActiveAt 在有效期内且 EFFECTIVE 状态应返回 true")
    void shouldBeActiveWhenEffectiveAndWithinValidity() {
      var grant = AuthorizationFixtures.effectiveAllowGrant();
      assertThat(grant.isActiveAt(LocalDateTime.now())).isTrue();
    }

    @Test
    @DisplayName("coversBusiness 匹配相同业务编码应返回 true")
    void shouldCoverBusinessWhenCodeMatches() {
      var grant = AuthorizationFixtures.effectiveAllowGrant();
      assertThat(grant.coversBusiness(new BusinessCode("BIZ-001"))).isTrue();
    }

    @Test
    @DisplayName("coversBusiness 不匹配不同业务编码应返回 false")
    void shouldNotCoverBusinessWhenCodeDiffers() {
      var grant = AuthorizationFixtures.effectiveAllowGrant();
      assertThat(grant.coversBusiness(new BusinessCode("BIZ-999"))).isFalse();
    }

    @Test
    @DisplayName("grants 匹配相同权限应返回 true")
    void shouldGrantWhenPermissionMatches() {
      var grant = AuthorizationFixtures.effectiveAllowGrant();
      var perm = AuthorizationFixtures.permission("BIZ-001", "ACT-VIEW");
      assertThat(grant.grants(perm)).isTrue();
    }
  }
}
