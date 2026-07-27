package com.example.iam.domain.authorization.event;

import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.DelegationType;
import com.example.iam.domain.authorization.aggregate.valueobject.OverrideMode;
import com.example.iam.domain.authorization.aggregate.valueobject.SubjectType;
import com.example.iam.types.PermissionRuleId;
import com.example.iam.types.PlanDelegationId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("authorization 域事件契约")
class AuthorizationEventsTest {

  private static final UserNo OPERATOR = UserNo.of("U-ADMIN");
  private static final PermissionRuleId RULE_ID = PermissionRuleId.of(1L);
  private static final PlanDelegationId DELEGATION_ID = PlanDelegationId.of(2L);

  @Test
  @DisplayName("PermissionRuleCreatedEvent 实现 DomainEvent 接口")
  void permissionRuleCreatedEvent_implementsDomainEvent() {
    PermissionRuleCreatedEvent event = PermissionRuleCreatedEvent.of(
        RULE_ID, "RULE-001", SubjectType.CUSTOMER, "CUST001",
        BusinessCode.of("ANNUITY_ESTABLISH"), OverrideMode.ADD, null, OPERATOR);

    assertThat(event).isInstanceOf(DomainEvent.class);
    assertThat(event.eventId()).isNotNull();
    assertThat(event.occurredOn()).isNotNull();
    assertThat(event.ruleId()).isEqualTo(RULE_ID);
    assertThat(event.ruleCode()).isEqualTo("RULE-001");
    assertThat(event.subjectType()).isEqualTo(SubjectType.CUSTOMER);
    assertThat(event.businessCode()).isEqualTo(BusinessCode.of("ANNUITY_ESTABLISH"));
    assertThat(event.overrideMode()).isEqualTo(OverrideMode.ADD);
    assertThat(event.operator()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("PermissionRuleDisabledEvent 实现 DomainEvent 接口")
  void permissionRuleDisabledEvent_implementsDomainEvent() {
    PermissionRuleDisabledEvent event = PermissionRuleDisabledEvent.of(
        RULE_ID, "RULE-001", OPERATOR);

    assertThat(event).isInstanceOf(DomainEvent.class);
    assertThat(event.eventId()).isNotNull();
    assertThat(event.occurredOn()).isNotNull();
    assertThat(event.ruleId()).isEqualTo(RULE_ID);
    assertThat(event.ruleCode()).isEqualTo("RULE-001");
    assertThat(event.operator()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("PermissionRuleEnabledEvent 实现 DomainEvent 接口")
  void permissionRuleEnabledEvent_implementsDomainEvent() {
    PermissionRuleEnabledEvent event = PermissionRuleEnabledEvent.of(
        RULE_ID, "RULE-001", OPERATOR);

    assertThat(event).isInstanceOf(DomainEvent.class);
    assertThat(event.eventId()).isNotNull();
    assertThat(event.occurredOn()).isNotNull();
    assertThat(event.ruleId()).isEqualTo(RULE_ID);
    assertThat(event.ruleCode()).isEqualTo("RULE-001");
    assertThat(event.operator()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("PlanDelegationCreatedEvent 实现 DomainEvent 接口")
  void planDelegationCreatedEvent_implementsDomainEvent() {
    PlanDelegationCreatedEvent event = PlanDelegationCreatedEvent.of(
        DELEGATION_ID, "DLG-001", "PLAN-A", "PLAN-B",
        DelegationType.ALL_OPERATORS, OPERATOR);

    assertThat(event).isInstanceOf(DomainEvent.class);
    assertThat(event.eventId()).isNotNull();
    assertThat(event.occurredOn()).isNotNull();
    assertThat(event.delegationId()).isEqualTo(DELEGATION_ID);
    assertThat(event.delegationCode()).isEqualTo("DLG-001");
    assertThat(event.delegatorPlanNo()).isEqualTo("PLAN-A");
    assertThat(event.delegateePlanNo()).isEqualTo("PLAN-B");
    assertThat(event.delegationType()).isEqualTo(DelegationType.ALL_OPERATORS);
    assertThat(event.operator()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("PlanDelegationActivatedEvent 实现 DomainEvent 接口")
  void planDelegationActivatedEvent_implementsDomainEvent() {
    PlanDelegationActivatedEvent event = PlanDelegationActivatedEvent.of(
        DELEGATION_ID, "DLG-001", "PLAN-B", OPERATOR);

    assertThat(event).isInstanceOf(DomainEvent.class);
    assertThat(event.eventId()).isNotNull();
    assertThat(event.occurredOn()).isNotNull();
    assertThat(event.delegationId()).isEqualTo(DELEGATION_ID);
    assertThat(event.delegationCode()).isEqualTo("DLG-001");
    assertThat(event.delegateePlanNo()).isEqualTo("PLAN-B");
    assertThat(event.operator()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("PlanDelegationRevokedEvent 实现 DomainEvent 接口")
  void planDelegationRevokedEvent_implementsDomainEvent() {
    PlanDelegationRevokedEvent event = PlanDelegationRevokedEvent.of(
        DELEGATION_ID, "DLG-001", "PLAN-B", "测试撤销", OPERATOR);

    assertThat(event).isInstanceOf(DomainEvent.class);
    assertThat(event.eventId()).isNotNull();
    assertThat(event.occurredOn()).isNotNull();
    assertThat(event.delegationId()).isEqualTo(DELEGATION_ID);
    assertThat(event.delegationCode()).isEqualTo("DLG-001");
    assertThat(event.delegateePlanNo()).isEqualTo("PLAN-B");
    assertThat(event.reason()).isEqualTo("测试撤销");
    assertThat(event.operator()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("每个事件的 of() 工厂方法生成唯一 eventId")
  void eventOf_generatesUniqueEventIds() {
    PermissionRuleCreatedEvent e1 = PermissionRuleCreatedEvent.of(
        RULE_ID, "RULE-001", SubjectType.CUSTOMER, "CUST001",
        BusinessCode.of("ANNUITY_ESTABLISH"), OverrideMode.ADD, null, OPERATOR);
    PermissionRuleCreatedEvent e2 = PermissionRuleCreatedEvent.of(
        RULE_ID, "RULE-001", SubjectType.CUSTOMER, "CUST001",
        BusinessCode.of("ANNUITY_ESTABLISH"), OverrideMode.ADD, null, OPERATOR);

    assertThat(e1.eventId()).isNotEqualTo(e2.eventId());
  }

  @Test
  @DisplayName("每个事件的 occurredOn 是当前时间附近")
  void eventOf_setsOccurredOnToNow() {
    LocalDateTime before = LocalDateTime.now().minusSeconds(1);

    PermissionRuleDisabledEvent event = PermissionRuleDisabledEvent.of(
        RULE_ID, "RULE-001", OPERATOR);

    LocalDateTime after = LocalDateTime.now().plusSeconds(1);
    assertThat(event.occurredOn()).isBetween(before, after);
  }
}
