package com.pension.permission.domain.assignment.aggregate;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.event.AssignmentCreated;
import com.pension.permission.domain.assignment.event.AssignmentDeactivated;
import com.pension.permission.domain.fixture.AssignmentFixtures;
import com.pension.permission.types.AssignmentId;
import com.pension.permission.types.AssignmentScopeDimension;
import com.pension.permission.types.RoleCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AgentIdentityAssignment 聚合根测试")
class AgentIdentityAssignmentTest {

  @Nested
  @DisplayName("创建 create")
  class CreateTest {

    @Test
    @DisplayName("应创建 ACTIVE 状态的分配并注册事件")
    void shouldCreateActiveAssignment() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");

      assertThat(assignment.isActive()).isTrue();
      assertThat(assignment.userNo()).isEqualTo(UserNo.of("user-1"));
      assertThat(assignment.roleCode()).isEqualTo(new RoleCode("ROLE_AGENT"));
      assertThat(assignment.domainEvents()).anyMatch(e -> e instanceof AssignmentCreated);
    }

    @Test
    @DisplayName("inheritable=true 但 scopeDimension 非 CUSTOMER 应抛异常")
    void shouldThrowWhenInheritableButNotCustomerScope() {
      assertThatThrownBy(() -> AgentIdentityAssignment.create(
        new AssignmentId("a-1"),
        UserNo.of("creator-1"),
        UserNo.of("user-1"),
        new RoleCode("ROLE_AGENT"),
        AssignmentScopeDimension.PLAN,
        "PLAN-001",
        true))
        .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("inheritable=true 且 scopeDimension=CUSTOMER 应创建成功")
    void shouldCreateWhenInheritableAndCustomerScope() {
      var assignment = AssignmentFixtures.inheritableCustomerAssignment("user-1", "ROLE_AGENT", "CUST-001");

      assertThat(assignment.isActive()).isTrue();
      assertThat(assignment.isInheritable()).isTrue();
    }
  }

  @Nested
  @DisplayName("角色变更 changeRole")
  class ChangeRoleTest {

    @Test
    @DisplayName("变更角色应更新 roleCode 并注册事件")
    void shouldChangeRole() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");

      assignment.changeRole(new RoleCode("ROLE_REVIEWER"));

      assertThat(assignment.roleCode()).isEqualTo(new RoleCode("ROLE_REVIEWER"));
    }

    @Test
    @DisplayName("变更到相同角色应保持不变")
    void shouldKeepSameRoleWhenNoChange() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");

      assignment.changeRole(new RoleCode("ROLE_AGENT"));

      assertThat(assignment.roleCode()).isEqualTo(new RoleCode("ROLE_AGENT"));
    }

    @Test
    @DisplayName("传入 null 角色应抛 IllegalArgumentException")
    void shouldThrowWhenRoleIsNull() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");

      assertThatThrownBy(() -> assignment.changeRole(null))
        .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("停用 deactivate")
  class DeactivateTest {

    @Test
    @DisplayName("停用活跃分配应转为 DEACTIVATED 并注册事件")
    void shouldDeactivateActiveAssignment() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");

      assignment.deactivate();

      assertThat(assignment.isActive()).isFalse();
      assertThat(assignment.domainEvents()).anyMatch(e -> e instanceof AssignmentDeactivated);
    }

    @Test
    @DisplayName("对已停用分配再次停用应幂等")
    void shouldBeIdempotentWhenAlreadyDeactivated() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");
      assignment.deactivate();
      var eventCountBefore = assignment.domainEvents().size();

      assignment.deactivate();

      assertThat(assignment.domainEvents()).hasSize(eventCountBefore);
    }
  }
}
