package com.example.auth.adapter.assignment;

import com.example.auth.api.dto.ParentPlanResponse;
import com.example.auth.api.dto.PlanMembersResponse;
import com.example.auth.api.query.GetParentPlanRequest;
import com.example.auth.api.query.ListPlanMembersRequest;
import com.example.auth.application.assignment.PlanDirectoryQueryService;
import com.example.shared.web.core.api.ApiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 批次 67：组织目录查询 Controller 的委托与响应包装。 */
@DisplayName("组织目录查询 Controller（批次 67）")
class PlanDirectoryControllerTest {

  private PlanDirectoryQueryService service;
  private PlanDirectoryController controller;

  @BeforeEach
  void setUp() {
    service = mock(PlanDirectoryQueryService.class);
    controller = new PlanDirectoryController(service);
  }

  @Test
  @DisplayName("/members 透传计划号与排除人并包装响应")
  void listMembers_delegatesAndWraps() {
    when(service.listMembers("P-1", "U-INIT")).thenReturn(List.of("U-1", "U-2"));

    ApiResult<PlanMembersResponse> result =
      controller.listMembers(new ListPlanMembersRequest("P-1", "U-INIT"));

    assertThat(result.data().userNos()).containsExactly("U-1", "U-2");
    verify(service).listMembers("P-1", "U-INIT");
  }

  @Test
  @DisplayName("/parent 透传计划号并包装可空父计划号")
  void getParentPlan_delegatesAndWraps() {
    when(service.getParentPlan("P-1")).thenReturn("P-0");

    ApiResult<ParentPlanResponse> result =
      controller.getParentPlan(new GetParentPlanRequest("P-1"));

    assertThat(result.data().parentPlanNo()).isEqualTo("P-0");
    verify(service).getParentPlan("P-1");
  }
}
