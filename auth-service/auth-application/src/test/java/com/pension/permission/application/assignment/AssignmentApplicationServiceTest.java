package com.pension.permission.application.assignment;

import com.example.shared.domain.event.DomainEvent;
import com.example.shared.domain.event.EventBus;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.domain.assignment.service.GrantProvisioningService;
import com.pension.permission.domain.assignment.service.PlanReachabilityService;
import com.pension.permission.types.AssignmentId;
import com.pension.permission.types.AssignmentScopeDimension;
import com.pension.permission.types.RoleCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AssignmentApplicationService 测试")
@ExtendWith(MockitoExtension.class)
class AssignmentApplicationServiceTest {

  @Mock
  private AssignmentRepository assignmentRepository;

  @Mock
  private GrantProvisioningService grantProvisioningService;

  @Mock
  private PlanReachabilityService planReachabilityService;

  @Mock
  private EventBus eventBus;

  @Mock
  private IdService idService;

  @InjectMocks
  private AssignmentApplicationService service;

  @Nested
  @DisplayName("createAssignment: 创建身份分配")
  class CreateAssignmentTest {

    @Test
    @DisplayName("应创建分配、委托 GrantProvisioningService、发布事件并返回 ID")
    void shouldCreateAssignmentAndDelegateProvisioningAndPublishEvents() {
      var assignmentId = new AssignmentId("a-1");
      var accountId = UserNo.of("user-1");
      var operator = UserNo.of("admin-1");
      var roleCode = new RoleCode("ROLE_AGENT");
      when(idService.nextId(AssignmentId.class)).thenReturn(assignmentId);
      var command = new CreateAssignmentCommand(
        accountId, roleCode, AssignmentScopeDimension.PLAN, "PLAN-001", false, operator);

      var result = service.createAssignment(command);

      assertThat(result).isEqualTo(assignmentId);

      var captor = ArgumentCaptor.forClass(AgentIdentityAssignment.class);
      verify(grantProvisioningService).onAssignmentCreated(captor.capture());
      var created = captor.getValue();
      assertThat(created.id()).isEqualTo(assignmentId);
      assertThat(created.userNo()).isEqualTo(accountId);
      assertThat(created.roleCode()).isEqualTo(roleCode);

      verify(eventBus, atLeastOnce()).publish(any());
    }
  }

  @Nested
  @DisplayName("changeRole: 变更角色")
  class ChangeRoleTest {

    @Test
    @DisplayName("应加载分配、委托 GrantProvisioningService 变更角色并发布事件")
    void shouldLoadAssignmentAndDelegateRoleChangeAndPublishEvents() {
      var assignmentId = new AssignmentId("a-1");
      var newRoleCode = new RoleCode("ROLE_REVIEW");
      var assignment = mock(AgentIdentityAssignment.class);
      var event = mock(DomainEvent.class);
      when(assignmentRepository.load(assignmentId)).thenReturn(Optional.of(assignment));
      when(assignment.domainEvents()).thenReturn(List.of(event));
      var command = new ChangeAssignmentRoleCommand(assignmentId, newRoleCode, UserNo.of("admin-1"));

      service.changeRole(command);

      verify(grantProvisioningService).onAssignmentRoleChanged(assignment, newRoleCode);
      verify(eventBus).publish(event);
      verify(assignment, never()).changeRole(any());
    }
  }

  @Nested
  @DisplayName("deactivate: 停用分配")
  class DeactivateTest {

    @Test
    @DisplayName("应加载分配、委托 GrantProvisioningService 停用并发布事件")
    void shouldLoadAssignmentAndDelegateDeactivationAndPublishEvents() {
      var assignmentId = new AssignmentId("a-1");
      var assignment = mock(AgentIdentityAssignment.class);
      var event = mock(DomainEvent.class);
      when(assignmentRepository.load(assignmentId)).thenReturn(Optional.of(assignment));
      when(assignment.domainEvents()).thenReturn(List.of(event));
      var command = new DeactivateAssignmentCommand(assignmentId, UserNo.of("admin-1"));

      service.deactivate(command);

      verify(grantProvisioningService).onAssignmentDeactivated(assignment);
      verify(eventBus).publish(event);
      verify(assignment, never()).deactivate();
    }
  }
}
