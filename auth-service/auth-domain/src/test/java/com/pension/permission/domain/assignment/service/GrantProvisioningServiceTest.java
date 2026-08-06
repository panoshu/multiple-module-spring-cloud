package com.pension.permission.domain.assignment.service;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.repository.AssignmentRepository;
import com.pension.permission.domain.fixture.AssignmentFixtures;
import com.pension.permission.domain.role.service.RoleTemplateResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GrantProvisioningService 测试")
class GrantProvisioningServiceTest {

  private final RoleTemplateResolver roleTemplateResolver = mock(RoleTemplateResolver.class);
  private final AssignmentRepository assignmentRepository = AssignmentFixtures.mockAssignmentRepository();
  private final GrantProvisioningService service =
    new GrantProvisioningService(roleTemplateResolver, assignmentRepository);

  @Nested
  @DisplayName("onAssignmentCreated")
  class OnAssignmentCreatedTest {

    @Test
    @DisplayName("模板存在时应保存分配")
    void shouldSaveAssignmentWhenTemplateExists() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");
      when(roleTemplateResolver.resolve(any(), any(), any()))
        .thenReturn(Optional.of(mock(com.pension.permission.domain.role.aggregate.RoleTemplate.class)));

      service.onAssignmentCreated(assignment);

      verify(assignmentRepository).save(assignment);
    }
  }

  @Nested
  @DisplayName("onAssignmentDeactivated")
  class OnAssignmentDeactivatedTest {

    @Test
    @DisplayName("应调用 deactivate 并保存")
    void shouldDeactivateAndSave() {
      var assignment = AssignmentFixtures.activeAssignment("user-1", "ROLE_AGENT");

      service.onAssignmentDeactivated(assignment);

      verify(assignmentRepository).save(assignment);
    }
  }
}
