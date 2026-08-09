package com.pension.permission.infrastructure.authorization.spi;

import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.ProductNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.domain.product.ProductGateway;
import com.pension.permission.types.AssignmentId;
import com.pension.permission.types.AssignmentScopeDimension;
import com.pension.permission.types.RoleCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("AssignmentBasedPlanMembershipLookup 测试")
@ExtendWith(MockitoExtension.class)
class AssignmentBasedPlanMembershipLookupTest {

  @Mock
  private AssignmentRepository assignmentRepository;

  @Mock
  private ProductGateway productGateway;

  @InjectMocks
  private AssignmentBasedPlanMembershipLookup lookup;

  private AgentIdentityAssignment assignment(
    String userNo, String roleCode, AssignmentScopeDimension dimension, String scopeValue) {
    return AgentIdentityAssignment.create(
      new AssignmentId("a-1"),
      UserNo.of("creator-1"),
      UserNo.of(userNo),
      new RoleCode(roleCode),
      dimension,
      scopeValue,
      false);
  }

  private AgentIdentityAssignment inheritableCustomerAssignment(
    String userNo, String roleCode, String scopeValue) {
    return AgentIdentityAssignment.create(
      new AssignmentId("a-2"),
      UserNo.of("creator-1"),
      UserNo.of(userNo),
      new RoleCode(roleCode),
      AssignmentScopeDimension.CUSTOMER,
      scopeValue,
      true);
  }

  @Nested
  @DisplayName("isMemberOf: 计划成员判定")
  class IsMemberOfTest {

    @Test
    @DisplayName("null userNo 应返回 false")
    void shouldReturnFalseWhenUserNoNull() {
      assertThat(lookup.isMemberOf(null, PlanNo.of("PLAN-001"))).isFalse();
    }

    @Test
    @DisplayName("null planNo 应返回 false")
    void shouldReturnFalseWhenPlanNoNull() {
      assertThat(lookup.isMemberOf(UserNo.of("user-1"), null)).isFalse();
    }

    @Test
    @DisplayName("无分配时应返回 false")
    void shouldReturnFalseWhenNoAssignment() {
      when(assignmentRepository.findActiveByAccount(UserNo.of("user-1")))
        .thenReturn(List.of());

      assertThat(lookup.isMemberOf(UserNo.of("user-1"), PlanNo.of("PLAN-001"))).isFalse();
    }

    @Test
    @DisplayName("PLAN 维度 scopeValue 匹配时应返回 true")
    void shouldReturnTrueWhenPlanScopeMatches() {
      var assignment = assignment("user-1", "ROLE_AGENT",
        AssignmentScopeDimension.PLAN, "PLAN-001");
      when(assignmentRepository.findActiveByAccount(UserNo.of("user-1")))
        .thenReturn(List.of(assignment));

      assertThat(lookup.isMemberOf(UserNo.of("user-1"), PlanNo.of("PLAN-001"))).isTrue();
    }

    @Test
    @DisplayName("PLAN 维度 scopeValue 不匹配时应返回 false")
    void shouldReturnFalseWhenPlanScopeNotMatch() {
      var assignment = assignment("user-1", "ROLE_AGENT",
        AssignmentScopeDimension.PLAN, "PLAN-999");
      when(assignmentRepository.findActiveByAccount(UserNo.of("user-1")))
        .thenReturn(List.of(assignment));

      assertThat(lookup.isMemberOf(UserNo.of("user-1"), PlanNo.of("PLAN-001"))).isFalse();
    }

    @Test
    @DisplayName("GLOBAL 维度应恒返回 true")
    void shouldReturnTrueForGlobalScope() {
      var assignment = assignment("user-1", "ROLE_AGENT",
        AssignmentScopeDimension.GLOBAL, "GLOBAL");
      when(assignmentRepository.findActiveByAccount(UserNo.of("user-1")))
        .thenReturn(List.of(assignment));

      assertThat(lookup.isMemberOf(UserNo.of("user-1"), PlanNo.of("ANY-PLAN"))).isTrue();
    }

    @Test
    @DisplayName("CUSTOMER 维度通过 ProductGateway 匹配时应返回 true")
    void shouldReturnTrueWhenCustomerScopeMatches() {
      var assignment = assignment("user-1", "ROLE_AGENT",
        AssignmentScopeDimension.CUSTOMER, "CUST-001");
      when(assignmentRepository.findActiveByAccount(UserNo.of("user-1")))
        .thenReturn(List.of(assignment));
      when(productGateway.plansOfCustomer(CustomerNo.of("CUST-001"), false))
        .thenReturn(List.of(PlanNo.of("PLAN-001"), PlanNo.of("PLAN-002")));

      assertThat(lookup.isMemberOf(UserNo.of("user-1"), PlanNo.of("PLAN-001"))).isTrue();
    }

    @Test
    @DisplayName("CUSTOMER 维度通过 ProductGateway 不匹配时应返回 false")
    void shouldReturnFalseWhenCustomerScopeNotMatch() {
      var assignment = assignment("user-1", "ROLE_AGENT",
        AssignmentScopeDimension.CUSTOMER, "CUST-001");
      when(assignmentRepository.findActiveByAccount(UserNo.of("user-1")))
        .thenReturn(List.of(assignment));
      when(productGateway.plansOfCustomer(CustomerNo.of("CUST-001"), false))
        .thenReturn(List.of(PlanNo.of("PLAN-999")));

      assertThat(lookup.isMemberOf(UserNo.of("user-1"), PlanNo.of("PLAN-001"))).isFalse();
    }

    @Test
    @DisplayName("PRODUCT 维度通过 ProductGateway 匹配时应返回 true")
    void shouldReturnTrueWhenProductScopeMatches() {
      var assignment = assignment("user-1", "ROLE_AGENT",
        AssignmentScopeDimension.PRODUCT, "PROD-001");
      when(assignmentRepository.findActiveByAccount(UserNo.of("user-1")))
        .thenReturn(List.of(assignment));
      when(productGateway.plansOfProduct(ProductNo.of("PROD-001")))
        .thenReturn(List.of(PlanNo.of("PLAN-001")));

      assertThat(lookup.isMemberOf(UserNo.of("user-1"), PlanNo.of("PLAN-001"))).isTrue();
    }

    @Test
    @DisplayName("多分配中有一条匹配时应返回 true")
    void shouldReturnTrueWhenAnyAssignmentMatches() {
      var a1 = assignment("user-1", "ROLE_AGENT",
        AssignmentScopeDimension.PLAN, "PLAN-999");
      var a2 = assignment("user-1", "ROLE_MANAGER",
        AssignmentScopeDimension.PLAN, "PLAN-001");
      when(assignmentRepository.findActiveByAccount(UserNo.of("user-1")))
        .thenReturn(List.of(a1, a2));

      assertThat(lookup.isMemberOf(UserNo.of("user-1"), PlanNo.of("PLAN-001"))).isTrue();
    }
  }

  @Nested
  @DisplayName("hasRole: 角色判定")
  class HasRoleTest {

    @Test
    @DisplayName("null roleCode 应返回 false")
    void shouldReturnFalseWhenRoleCodeNull() {
      assertThat(lookup.hasRole(UserNo.of("user-1"), PlanNo.of("PLAN-001"), null)).isFalse();
    }

    @Test
    @DisplayName("角色匹配且计划匹配时应返回 true")
    void shouldReturnTrueWhenRoleAndPlanMatch() {
      var assignment = assignment("user-1", "ROLE_AGENT",
        AssignmentScopeDimension.PLAN, "PLAN-001");
      when(assignmentRepository.findActiveByAccount(UserNo.of("user-1")))
        .thenReturn(List.of(assignment));

      assertThat(lookup.hasRole(UserNo.of("user-1"), PlanNo.of("PLAN-001"),
        new RoleCode("ROLE_AGENT"))).isTrue();
    }

    @Test
    @DisplayName("计划匹配但角色不匹配时应返回 false")
    void shouldReturnFalseWhenPlanMatchButRoleNotMatch() {
      var assignment = assignment("user-1", "ROLE_AGENT",
        AssignmentScopeDimension.PLAN, "PLAN-001");
      when(assignmentRepository.findActiveByAccount(UserNo.of("user-1")))
        .thenReturn(List.of(assignment));

      assertThat(lookup.hasRole(UserNo.of("user-1"), PlanNo.of("PLAN-001"),
        new RoleCode("ROLE_MANAGER"))).isFalse();
    }

    @Test
    @DisplayName("角色匹配但计划不匹配时应返回 false")
    void shouldReturnFalseWhenRoleMatchButPlanNotMatch() {
      var assignment = assignment("user-1", "ROLE_AGENT",
        AssignmentScopeDimension.PLAN, "PLAN-999");
      when(assignmentRepository.findActiveByAccount(UserNo.of("user-1")))
        .thenReturn(List.of(assignment));

      assertThat(lookup.hasRole(UserNo.of("user-1"), PlanNo.of("PLAN-001"),
        new RoleCode("ROLE_AGENT"))).isFalse();
    }
  }
}
