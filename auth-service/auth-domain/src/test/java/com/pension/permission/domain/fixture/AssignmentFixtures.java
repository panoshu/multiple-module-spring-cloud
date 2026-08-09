package com.pension.permission.domain.fixture;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.types.AssignmentId;
import com.pension.permission.types.AssignmentScopeDimension;
import com.pension.permission.types.RoleCode;

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
