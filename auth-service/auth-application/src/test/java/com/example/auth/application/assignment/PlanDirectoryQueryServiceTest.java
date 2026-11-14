package com.example.auth.application.assignment;

import com.example.auth.domain.assignment.aggregate.AgentIdentityAssignment;
import com.example.auth.domain.assignment.repository.AssignmentRepository;
import com.example.auth.domain.assignment.service.PlanReachabilityService;
import com.example.auth.domain.product.PlanSnapshot;
import com.example.auth.domain.product.ProductGateway;
import com.example.auth.types.AssignmentScopeDimension;
import com.example.core.types.CustomerNo;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.core.types.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 批次 67：组织目录查询应用服务契约（先红后绿）。 */
@DisplayName("组织目录查询应用服务（批次 67）")
class PlanDirectoryQueryServiceTest {

  private AssignmentRepository assignmentRepository;
  private ProductGateway productGateway;
  private PlanDirectoryQueryService service;

  @BeforeEach
  void setUp() {
    assignmentRepository = mock(AssignmentRepository.class);
    productGateway = mock(ProductGateway.class);
    service = new PlanDirectoryQueryService(
      new PlanReachabilityService(assignmentRepository, productGateway), productGateway);
  }

  @Test
  @DisplayName("计划成员按 PLAN 精确 scope 命中，去重并排除发起人")
  void listMembers_hitsPlanScopeAndExcludesInitiator() {
    PlanSnapshot planSnapshot = snapshot("P-1", "P-0");
    when(productGateway.findPlan(PlanNo.of("P-1"))).thenReturn(Optional.of(planSnapshot));
    // PlanReachabilityService 走 ProductGateway.requirePlan 默认方法，mock 需显式 stub
    when(productGateway.requirePlan(PlanNo.of("P-1"))).thenReturn(planSnapshot);
    when(productGateway.ancestorsOf(any())).thenReturn(List.of(CustomerNo.of("C-1")));
    // 先构造 mock 列表再 stubbing，避免 when(...) 参数求值期间嵌套注册 stubbing
    List<AgentIdentityAssignment> assignments = List.of(
      assignment("U-1"), assignment("U-2"), assignment("U-1"));
    when(assignmentRepository.findAllActive()).thenReturn(assignments);

    assertThat(service.listMembers("P-1", "U-2")).containsExactly("U-1");
  }

  @Test
  @DisplayName("组织目录无该计划时 fail-closed 返回空且不查分配")
  void listMembers_failClosedWhenPlanUnknown() {
    when(productGateway.findPlan(PlanNo.of("P-X"))).thenReturn(Optional.empty());

    assertThat(service.listMembers("P-X", null)).isEmpty();
    verify(assignmentRepository, never()).findAllActive();
  }

  @Test
  @DisplayName("父计划取 PlanSnapshot.parentPlanNo")
  void getParentPlan_returnsParentPlanNo() {
    when(productGateway.findPlan(PlanNo.of("P-1"))).thenReturn(Optional.of(snapshot("P-1", "P-0")));

    assertThat(service.getParentPlan("P-1")).isEqualTo("P-0");
  }

  @Test
  @DisplayName("根计划无父计划时返回 null")
  void getParentPlan_returnsNullForRoot() {
    when(productGateway.findPlan(PlanNo.of("P-0"))).thenReturn(Optional.of(snapshot("P-0", null)));

    assertThat(service.getParentPlan("P-0")).isNull();
  }

  @Test
  @DisplayName("未知计划查询父计划 fail-closed 返回 null")
  void getParentPlan_failClosedWhenUnknown() {
    when(productGateway.findPlan(PlanNo.of("P-X"))).thenReturn(Optional.empty());

    assertThat(service.getParentPlan("P-X")).isNull();
  }

  private PlanSnapshot snapshot(String planNo, String parentPlanNo) {
    return new PlanSnapshot(PlanNo.of(planNo), ProductNo.of("PROD-1"), CustomerNo.of("C-1"),
      Optional.ofNullable(parentPlanNo).map(PlanNo::of), "示例计划", Instant.EPOCH);
  }

  private AgentIdentityAssignment assignment(String userNo) {
    AgentIdentityAssignment a = mock(AgentIdentityAssignment.class);
    when(a.scopeDimension()).thenReturn(AssignmentScopeDimension.PLAN);
    when(a.scopeValue()).thenReturn("P-1");
    when(a.userNo()).thenReturn(UserNo.of(userNo));
    return a;
  }
}
