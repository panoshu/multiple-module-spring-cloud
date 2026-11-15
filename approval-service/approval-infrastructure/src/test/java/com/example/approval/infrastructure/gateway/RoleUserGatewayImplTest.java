package com.example.approval.infrastructure.gateway;

import com.example.auth.api.PlanDirectoryApi;
import com.example.auth.api.RoleUserQueryApi;
import com.example.auth.api.dto.ParentPlanResponse;
import com.example.auth.api.dto.PlanMembersResponse;
import com.example.auth.api.dto.RoleUserBatchResponse;
import com.example.auth.api.query.GetParentPlanRequest;
import com.example.auth.api.query.ListPlanMembersRequest;
import com.example.auth.api.query.ListRoleUsersRequest;
import com.example.core.types.PlanNo;
import com.example.core.types.UserNo;
import com.example.shared.web.core.api.ApiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 批次 60/68/69：{@code RoleUserGatewayImpl} 接入 auth-api 的角色查询、同计划成员
 * 与父计划成员查询，以及 fail-closed 降级。
 */
@DisplayName("角色用户网关接入 auth-api（批次 60/68/69）")
class RoleUserGatewayImplTest {

  private RoleUserQueryApi roleUserQueryApi;
  private PlanDirectoryApi planDirectoryApi;
  private RoleUserGatewayImpl gateway;

  @BeforeEach
  void setUp() {
    roleUserQueryApi = mock(RoleUserQueryApi.class);
    planDirectoryApi = mock(PlanDirectoryApi.class);
    gateway = new RoleUserGatewayImpl(roleUserQueryApi, planDirectoryApi);
  }

  @Test
  @DisplayName("按角色展开响应并把 planNo 透传给 auth-api")
  void getUsersByRoles_mapsResponseAndPassesPlanNo() {
    when(roleUserQueryApi.listByRoles(any())).thenReturn(ApiResult.success(
      new RoleUserBatchResponse(Map.of("R1", List.of("U-1", "U-2")))));

    Map<String, List<UserNo>> result =
      gateway.getUsersByRoles(List.of("R1"), PlanNo.of("P-1"));

    assertThat(result.get("R1")).containsExactly(UserNo.of("U-1"), UserNo.of("U-2"));
    ArgumentCaptor<ListRoleUsersRequest> captor =
      ArgumentCaptor.forClass(ListRoleUsersRequest.class);
    verify(roleUserQueryApi).listByRoles(captor.capture());
    assertThat(captor.getValue().roleCodes()).containsExactly("R1");
    assertThat(captor.getValue().planNo()).isEqualTo("P-1");
  }

  @Test
  @DisplayName("角色列表为空时短路，不发起跨服务调用")
  void getUsersByRoles_emptyRoleIdsSkipsCall() {
    assertThat(gateway.getUsersByRoles(List.of(), PlanNo.of("P-1"))).isEmpty();
    verifyNoInteractions(roleUserQueryApi);
  }

  @Test
  @DisplayName("auth-api 返回 null 时 fail-closed 返回空")
  void getUsersByRoles_failsClosedOnNullResponse() {
    when(roleUserQueryApi.listByRoles(any())).thenReturn(null);

    assertThat(gateway.getUsersByRoles(List.of("R1"), PlanNo.of("P-1"))).isEmpty();
  }

  @Test
  @DisplayName("auth-api 抛异常时 fail-closed 返回空")
  void getUsersByRoles_failsClosedOnException() {
    when(roleUserQueryApi.listByRoles(any())).thenThrow(new IllegalStateException("auth down"));

    assertThat(gateway.getUsersByRoles(List.of("R1"), PlanNo.of("P-1"))).isEmpty();
  }

  @Test
  @DisplayName("同计划成员映射响应并把 planNo / excludeUserNo 透传给 /members")
  void getUsersInSamePlan_mapsResponseAndPassesRequest() {
    when(planDirectoryApi.listMembers(any())).thenReturn(ApiResult.success(
      new PlanMembersResponse(List.of("U-1", "U-2"))));

    List<UserNo> result = gateway.getUsersInSamePlan(PlanNo.of("P-1"), UserNo.of("U-INIT"));

    assertThat(result).containsExactly(UserNo.of("U-1"), UserNo.of("U-2"));
    ArgumentCaptor<ListPlanMembersRequest> captor =
      ArgumentCaptor.forClass(ListPlanMembersRequest.class);
    verify(planDirectoryApi).listMembers(captor.capture());
    assertThat(captor.getValue().planNo()).isEqualTo("P-1");
    assertThat(captor.getValue().excludeUserNo()).isEqualTo("U-INIT");
  }

  @Test
  @DisplayName("计划号为 null 时短路，不发起跨服务调用")
  void getUsersInSamePlan_nullPlanSkipsCall() {
    assertThat(gateway.getUsersInSamePlan(null, UserNo.of("U-INIT"))).isEmpty();
    verifyNoInteractions(planDirectoryApi);
  }

  @Test
  @DisplayName("/members 返回 null 时 fail-closed 返回空")
  void getUsersInSamePlan_failsClosedOnNullResponse() {
    when(planDirectoryApi.listMembers(any())).thenReturn(null);

    assertThat(gateway.getUsersInSamePlan(PlanNo.of("P-1"), null)).isEmpty();
  }

  @Test
  @DisplayName("/members 抛异常时 fail-closed 返回空")
  void getUsersInSamePlan_failsClosedOnException() {
    when(planDirectoryApi.listMembers(any()))
      .thenThrow(new IllegalStateException("auth down"));

    assertThat(gateway.getUsersInSamePlan(PlanNo.of("P-1"), null)).isEmpty();
  }

  @Test
  @DisplayName("父计划成员按 /parent 结果再调 /members，并透传父计划号与排除人")
  void getUsersInParentPlan_mapsResponseAndPassesRequests() {
    when(planDirectoryApi.getParentPlan(any())).thenReturn(ApiResult.success(
      new ParentPlanResponse("P-0")));
    when(planDirectoryApi.listMembers(any())).thenReturn(ApiResult.success(
      new PlanMembersResponse(List.of("U-P1"))));

    List<UserNo> result = gateway.getUsersInParentPlan(PlanNo.of("P-1"), UserNo.of("U-INIT"));

    assertThat(result).containsExactly(UserNo.of("U-P1"));
    ArgumentCaptor<GetParentPlanRequest> parentCaptor =
      ArgumentCaptor.forClass(GetParentPlanRequest.class);
    verify(planDirectoryApi).getParentPlan(parentCaptor.capture());
    assertThat(parentCaptor.getValue().planNo()).isEqualTo("P-1");
    ArgumentCaptor<ListPlanMembersRequest> membersCaptor =
      ArgumentCaptor.forClass(ListPlanMembersRequest.class);
    verify(planDirectoryApi).listMembers(membersCaptor.capture());
    assertThat(membersCaptor.getValue().planNo()).isEqualTo("P-0");
    assertThat(membersCaptor.getValue().excludeUserNo()).isEqualTo("U-INIT");
  }

  @Test
  @DisplayName("无父计划时 fail-closed 返回空，不再调 /members")
  void getUsersInParentPlan_failsClosedWhenNoParent() {
    when(planDirectoryApi.getParentPlan(any())).thenReturn(ApiResult.success(
      new ParentPlanResponse(null)));

    assertThat(gateway.getUsersInParentPlan(PlanNo.of("P-1"), null)).isEmpty();
    verify(planDirectoryApi, never()).listMembers(any());
  }

  @Test
  @DisplayName("/parent 抛异常时 fail-closed 返回空")
  void getUsersInParentPlan_failsClosedOnException() {
    when(planDirectoryApi.getParentPlan(any()))
      .thenThrow(new IllegalStateException("auth down"));

    assertThat(gateway.getUsersInParentPlan(PlanNo.of("P-1"), null)).isEmpty();
  }

  @Test
  @DisplayName("计划号为 null 时短路，不发起跨服务调用")
  void getUsersInParentPlan_nullPlanSkipsCall() {
    assertThat(gateway.getUsersInParentPlan(null, null)).isEmpty();
    verifyNoInteractions(planDirectoryApi);
  }
}
