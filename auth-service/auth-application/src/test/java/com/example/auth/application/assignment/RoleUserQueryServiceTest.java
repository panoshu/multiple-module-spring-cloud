package com.example.auth.application.assignment;

import com.example.auth.domain.assignment.aggregate.AgentIdentityAssignment;
import com.example.auth.domain.assignment.repository.AssignmentRepository;
import com.example.auth.types.RoleCode;
import com.example.core.types.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 批次 59：按角色分组用户编号的应用服务契约。 */
@DisplayName("角色用户查询服务（批次 59）")
class RoleUserQueryServiceTest {

  private AssignmentRepository assignmentRepository;
  private RoleUserQueryService service;

  @BeforeEach
  void setUp() {
    assignmentRepository = mock(AssignmentRepository.class);
    service = new RoleUserQueryService(assignmentRepository);
  }

  @Test
  @DisplayName("按角色分组并按用户编号去重")
  void listByRoles_groupsByRoleCodeAndDedupes() {
    // 先构造 mock 列表再 stubbing，避免在 when(...) 参数求值期间嵌套注册 stubbing
    List<AgentIdentityAssignment> assignments = List.of(
      assignment("R1", "U-1"),
      assignment("R1", "U-2"),
      assignment("R1", "U-1"),
      assignment("R2", "U-3"));
    when(assignmentRepository.findActiveByRoleCodesAndPlan(List.of("R1", "R2"), "P-1"))
      .thenReturn(assignments);

    Map<String, List<String>> result = service.listByRoles(List.of("R1", "R2"), "P-1");

    assertThat(result.get("R1")).containsExactly("U-1", "U-2");
    assertThat(result.get("R2")).containsExactly("U-3");
  }

  @Test
  @DisplayName("无生效分配时返回空 Map")
  void listByRoles_emptyWhenNoAssignments() {
    when(assignmentRepository.findActiveByRoleCodesAndPlan(List.of("R1"), "P-1"))
      .thenReturn(List.of());

    assertThat(service.listByRoles(List.of("R1"), "P-1")).isEmpty();
  }

  private AgentIdentityAssignment assignment(String roleCode, String userNo) {
    AgentIdentityAssignment assignment = mock(AgentIdentityAssignment.class);
    when(assignment.roleCode()).thenReturn(new RoleCode(roleCode));
    when(assignment.userNo()).thenReturn(UserNo.of(userNo));
    return assignment;
  }
}
