package com.example.approval.infrastructure.contract;

import com.example.approval.api.event.ApprovalInstanceApprovedEventDTO;
import com.example.approval.api.event.ApprovalInstanceCreatedEventDTO;
import com.example.approval.api.event.ApprovalInstanceRejectedEventDTO;
import com.example.approval.api.event.ApprovalInstanceWithdrawnEventDTO;
import com.example.approval.api.request.StartApprovalRequest;
import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.domain.event.ApprovalInstanceApproved;
import com.example.approval.domain.event.ApprovalInstanceCreated;
import com.example.approval.domain.event.ApprovalInstanceRejected;
import com.example.approval.domain.event.ApprovalInstanceWithdrawn;
import com.example.approval.domain.repository.ApprovalInstanceRepository;
import com.example.approval.domain.valueobject.FlowVersion;
import com.example.approval.infrastructure.entity.ApprovalInstanceDO;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.ApprovalInstanceId;
import com.example.core.types.ApplicationId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("审批服务 requestNo 契约形状")
class ApprovalRequestNoContractTest {

  @Test
  @DisplayName("StartApprovalRequest 携带 requestNo")
  void startApprovalRequest_carriesRequestNo() {
    StartApprovalRequest request = new StartApprovalRequest(
      null, "BIZ-1", "ANNUITY", "U-1", "SE-9");

    assertThat(request.requestNo()).isEqualTo("SE-9");
  }

  @Test
  @DisplayName("旧 4 参构造仍可用且 requestNo 为空（可空过渡）")
  void startApprovalRequest_legacyConstructorDefaultsToNull() {
    StartApprovalRequest request = new StartApprovalRequest(
      null, "BIZ-1", "ANNUITY", "U-1");

    assertThat(request.requestNo()).isNull();
  }

  @Test
  @DisplayName("审批结果事件 DTO 携带 requestNo")
  void approvedEvent_carriesRequestNo() {
    ApprovalInstanceApprovedEventDTO dto = new ApprovalInstanceApprovedEventDTO(
      "EVT-1", "1", "BIZ-1", "ANNUITY", LocalDateTime.now(), "SE-9");

    assertThat(dto.requestNo()).isEqualTo("SE-9");
  }

  @Test
  @DisplayName("旧 5 参事件构造仍可用且 requestNo 为空")
  void approvedEvent_legacyConstructorDefaultsToNull() {
    ApprovalInstanceApprovedEventDTO dto = new ApprovalInstanceApprovedEventDTO(
      "EVT-1", "1", "BIZ-1", "ANNUITY", LocalDateTime.now());

    assertThat(dto.requestNo()).isNull();
  }

  @Test
  @DisplayName("4 个审批结果事件 DTO 均声明 requestNo")
  void approvalEventDtos_declareRequestNo() {
    assertThat(List.of(
      ApprovalInstanceApprovedEventDTO.class,
      ApprovalInstanceCreatedEventDTO.class,
      ApprovalInstanceRejectedEventDTO.class,
      ApprovalInstanceWithdrawnEventDTO.class))
      .allSatisfy(type -> assertThat(
        Arrays.stream(type.getRecordComponents())
          .anyMatch(component -> component.getName().equals("requestNo")))
        .as(type.getSimpleName() + " 应声明 requestNo").isTrue());
  }

  @Test
  @DisplayName("审批聚合、仓储、DO 与领域事件均具备 requestNo 契约")
  void approvalDomain_declaresRequestNo() throws NoSuchMethodException {
    assertThat(ApprovalInstance.class.getDeclaredMethod("requestNo")).isNotNull();
    assertThat(ApprovalInstance.class.getDeclaredMethod("create",
      ApprovalInstanceId.class, ApprovalFlowId.class, FlowVersion.class,
      ApplicationId.class, String.class, String.class, String.class, String.class)).isNotNull();
    assertThat(ApprovalInstanceRepository.class.getDeclaredMethod("findByRequestNo", String.class)).isNotNull();
    assertThat(ApprovalInstanceDO.class.getDeclaredMethod("getRequestNo")).isNotNull();
    assertThat(List.of(ApprovalInstanceCreated.class, ApprovalInstanceApproved.class,
      ApprovalInstanceRejected.class, ApprovalInstanceWithdrawn.class))
      .allSatisfy(type -> assertThat(
        Arrays.stream(type.getRecordComponents())
          .anyMatch(component -> component.getName().equals("requestNo")))
        .as(type.getSimpleName() + " 应声明 requestNo").isTrue());
  }
}
