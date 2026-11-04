package com.example.auth.domain.fixture;

import com.example.core.types.UserNo;
import com.example.auth.domain.assignment.aggregate.AgentIdentityAssignment;
import com.example.auth.domain.assignment.repository.AssignmentRepository;
import com.example.auth.types.AssignmentId;
import com.example.auth.types.AssignmentScopeDimension;
import com.example.auth.types.RoleCode;

import static org.mockito.Mockito.mock;

/**
 * assignment 域测试数据工厂。
 */
public final class AssignmentFixtures {

  private AssignmentFixtures() {
  }

  public static AgentIdentityAssignment activeAssignment(String userNo, String roleCode) {
    return AgentIdentityAssignment.create(
      new AssignmentId("a-1"),
      UserNo.of("creator-1"),
      UserNo.of(userNo),
      new RoleCode(roleCode),
      AssignmentScopeDimension.PLAN,
      "PLAN-001",
      false);
  }

  public static AgentIdentityAssignment inheritableCustomerAssignment(String userNo, String roleCode, String customerValue) {
    return AgentIdentityAssignment.create(
      new AssignmentId("a-2"),
      UserNo.of("creator-1"),
      UserNo.of(userNo),
      new RoleCode(roleCode),
      AssignmentScopeDimension.CUSTOMER,
      customerValue,
      true);
  }

  public static AssignmentRepository mockAssignmentRepository() {
    return mock(AssignmentRepository.class);
  }
}
