package com.example.core.application.business.guard;

import com.example.core.api.context.BusinessMetaContext;
import com.example.shared.security.SessionClaims;
import com.example.shared.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DefaultBusinessAccessGuard 单元测试
 *
 * @author panoshu
 */
class DefaultBusinessAccessGuardTest {

  private DefaultBusinessAccessGuard guard;

  @BeforeEach
  void setUp() {
    guard = new DefaultBusinessAccessGuard();
  }

  private SessionClaims internetSession(boolean isProxy, Set<String> delegatedPlanNos, Set<String> permissions) {
    return new SessionClaims(
      "U001", "USER", "alice", "Alice",
      "INTERNET", "CLI001", "127.0.0.1",
      "C001", "Customer A",
      "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
      isProxy, "U002", "bob",
      false, null, null,
      permissions, delegatedPlanNos
    );
  }

  private SessionClaims branchSession(boolean hasSecondaryAuth) {
    return new SessionClaims(
      "U001", "USER", "alice", "Alice",
      "BRANCH", "CLI001", "127.0.0.1",
      "C001", "Customer A",
      "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
      false, null, null,
      hasSecondaryAuth, hasSecondaryAuth ? 100L : null, hasSecondaryAuth ? "U003" : null,
      Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"), Set.of()
    );
  }

  private SessionClaims hqSession() {
    return new SessionClaims(
      "U001", "USER", "alice", "Alice",
      "HQ", "CLI001", "127.0.0.1",
      "C001", "Customer A",
      "P001", "Plan A", "PRD001", "Product A", "MODEL_A", "CJP",
      false, null, null,
      false, null, null,
      Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"), Set.of()
    );
  }

  private BusinessMetaContext metaWith(String businessType, String planNo) {
    return new BusinessMetaContext(businessType, planNo, "C001", "Customer A",
      "PRD001", "Product A", "Plan A", "MODEL_A", "CJP");
  }

  @Test
  void should_pass_for_internet_non_proxy_with_permission() {
    SessionClaims session = internetSession(false, Set.of(), Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"));
    assertThatCode(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P001")))
      .doesNotThrowAnyException();
  }

  @Test
  void should_fail_when_business_type_permission_missing() {
    SessionClaims session = internetSession(false, Set.of(), Set.of());
    assertThatThrownBy(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P001")))
      .isInstanceOf(BusinessException.class)
      .extracting(ex -> ((BusinessException) ex).displayMessage())
      .asString()
      .contains("无办理权限");
  }

  @Test
  void should_fail_when_plan_no_mismatch() {
    SessionClaims session = internetSession(false, Set.of(), Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"));
    assertThatThrownBy(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P999")))
      .isInstanceOf(BusinessException.class)
      .extracting(ex -> ((BusinessException) ex).displayMessage())
      .asString()
      .contains("计划不一致");
  }

  @Test
  void should_pass_for_internet_proxy_with_delegated_plan() {
    SessionClaims session = internetSession(true, Set.of("P001"), Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"));
    assertThatCode(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P001")))
      .doesNotThrowAnyException();
  }

  @Test
  void should_fail_for_internet_proxy_without_delegation() {
    SessionClaims session = internetSession(true, Set.of(), Set.of("BUSINESS_ANNUITY_OPEN_HANDLE"));
    assertThatThrownBy(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P001")))
      .isInstanceOf(BusinessException.class)
      .extracting(ex -> ((BusinessException) ex).displayMessage())
      .asString()
      .contains("无代办权限");
  }

  @Test
  void should_pass_for_branch_with_secondary_auth() {
    SessionClaims session = branchSession(true);
    assertThatCode(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P001")))
      .doesNotThrowAnyException();
  }

  @Test
  void should_fail_for_branch_without_secondary_auth() {
    SessionClaims session = branchSession(false);
    assertThatThrownBy(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P001")))
      .isInstanceOf(BusinessException.class)
      .extracting(ex -> ((BusinessException) ex).displayMessage())
      .asString()
      .contains("需要二次授权");
  }

  @Test
  void should_pass_for_hq_channel() {
    SessionClaims session = hqSession();
    assertThatCode(() -> guard.checkCanHandle(session, metaWith("ANNUITY_OPEN", "P001")))
      .doesNotThrowAnyException();
  }
}
