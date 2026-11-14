package com.example.approval.infrastructure.gateway;

import com.example.auth.api.RoleUserQueryApi;
import com.example.auth.api.dto.RoleUserBatchResponse;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 批次 60：{@code RoleUserGatewayImpl} 接入 auth-api 的批量角色查询与 fail-closed 降级。
 */
@DisplayName("角色用户网关接入 auth-api（批次 60）")
class RoleUserGatewayImplTest {

  private RoleUserQueryApi roleUserQueryApi;
  private RoleUserGatewayImpl gateway;

  @BeforeEach
  void setUp() {
    roleUserQueryApi = mock(RoleUserQueryApi.class);
    gateway = new RoleUserGatewayImpl(roleUserQueryApi);
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
  @DisplayName("getUsersInSamePlan 仍为未接线 stub（无消费方）")
  void getUsersInSamePlan_stillStub() {
    assertThat(gateway.getUsersInSamePlan(PlanNo.of("P-1"), UserNo.of("U-1"))).isEmpty();
  }
}
