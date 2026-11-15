package com.example.auth.adapter.assignment;

import com.example.auth.api.dto.RoleUserBatchResponse;
import com.example.auth.api.query.ListRoleUsersRequest;
import com.example.auth.application.assignment.RoleUserQueryService;
import com.example.shared.web.core.api.ApiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 批次 59：角色用户查询 Controller 的委托与响应包装。 */
@DisplayName("角色用户查询 Controller（批次 59）")
class RoleUserQueryControllerTest {

  private RoleUserQueryService service;
  private RoleUserQueryController controller;

  @BeforeEach
  void setUp() {
    service = mock(RoleUserQueryService.class);
    controller = new RoleUserQueryController(service);
    when(service.listByRoles(any(), any()))
      .thenReturn(Map.of("AGENT_REVIEW", List.of("U-1", "U-2")));
  }

  @Test
  @DisplayName("透传请求参数并包装为 RoleUserBatchResponse")
  void listByRoles_delegatesAndWraps() {
    ListRoleUsersRequest request = new ListRoleUsersRequest(List.of("AGENT_REVIEW"), "P-1");

    ApiResult<RoleUserBatchResponse> result = controller.listByRoles(request);

    assertThat(result.data().usersByRole().get("AGENT_REVIEW")).containsExactly("U-1", "U-2");
    verify(service).listByRoles(List.of("AGENT_REVIEW"), "P-1");
  }
}
