package com.example.auth.domain.assignment.service;

import com.example.core.types.UserNo;
import com.example.auth.domain.assignment.repository.AssignmentRepository;
import com.example.auth.domain.fixture.AssignmentFixtures;
import com.example.auth.domain.fixture.AuthorizationFixtures;
import com.example.auth.domain.product.ProductGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("PlanReachabilityService 测试")
class PlanReachabilityServiceTest {

  private final AssignmentRepository assignmentRepository = AssignmentFixtures.mockAssignmentRepository();
  private final ProductGateway productGateway = AuthorizationFixtures.mockProductGateway();
  private final PlanReachabilityService service =
    new PlanReachabilityService(assignmentRepository, productGateway);

  @Nested
  @DisplayName("listSelectablePlans")
  class ListSelectablePlansTest {

    @Test
    @DisplayName("无活跃分配时应返回空列表")
    void shouldReturnEmptyWhenNoAssignment() {
      when(assignmentRepository.findActiveByAccount(UserNo.of("user-1")))
        .thenReturn(List.of());

      var plans = service.listSelectablePlans(UserNo.of("user-1"));

      assertThat(plans).isEmpty();
    }
  }
}
