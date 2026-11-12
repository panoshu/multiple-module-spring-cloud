package com.example.approval.application.service;

import com.example.approval.api.request.StartApprovalRequest;
import com.example.approval.domain.aggregate.root.ApprovalIdGenerator;
import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.domain.repository.ApprovalFlowRepository;
import com.example.approval.domain.repository.ApprovalInstanceRepository;
import com.example.approval.types.ApprovalInstanceId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.contract.IdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 审批服务 requestNo 幂等行为（spec §12.3 / §21.2）。 */
@DisplayName("审批实例启动的 requestNo 幂等")
class ApprovalInstanceServiceTest {

  private ApprovalInstanceRepository instanceRepository;
  private ApprovalFlowRepository flowRepository;
  private EventBus eventBus;
  private IdService idService;
  private ApprovalIdGenerator idGenerator;
  private ApprovalInstanceService service;

  @BeforeEach
  void setUp() {
    instanceRepository = mock(ApprovalInstanceRepository.class);
    flowRepository = mock(ApprovalFlowRepository.class);
    eventBus = mock(EventBus.class);
    idService = mock(IdService.class);
    idGenerator = mock(ApprovalIdGenerator.class);
    service = new ApprovalInstanceService(
      instanceRepository, flowRepository, eventBus, idService, idGenerator);
  }

  @Test
  @DisplayName("同一 requestNo 重复启动直接返回原实例，不重复创建")
  void startApproval_sameRequestNo_returnsExistingWithoutCreating() {
    ApprovalInstance existing = mock(ApprovalInstance.class);
    when(existing.id()).thenReturn(ApprovalInstanceId.of(100L));
    when(instanceRepository.findByRequestNo("SE-9")).thenReturn(Optional.of(existing));
    StartApprovalRequest request = new StartApprovalRequest(
      null, "BIZ-1", "ANNUITY", "U-1", "SE-9");

    ApprovalInstanceId result = service.startApproval(request);

    assertThat(result).isEqualTo(ApprovalInstanceId.of(100L));
    verify(instanceRepository).findByRequestNo("SE-9");
    verify(instanceRepository, never()).save(any());
    verifyNoInteractions(flowRepository, eventBus, idService, idGenerator);
  }

  @Test
  @DisplayName("requestNo 为空时不查重（过渡期旧调用方走原创建路径）")
  void startApproval_blankRequestNo_doesNotQueryByRequestNo() {
    when(flowRepository.load(any())).thenReturn(Optional.empty());
    StartApprovalRequest request = new StartApprovalRequest(
      null, "BIZ-1", "ANNUITY", "U-1", "   ");

    assertThatThrownBy(() -> service.startApproval(request))
      .isInstanceOf(DomainException.class);

    verify(instanceRepository, never()).findByRequestNo(any());
  }

  @Test
  @DisplayName("findByRequestNo 委托仓储并返回原实例 ID（响应丢失对账）")
  void findByRequestNo_delegatesToRepository() {
    ApprovalInstance existing = mock(ApprovalInstance.class);
    when(existing.id()).thenReturn(ApprovalInstanceId.of(100L));
    when(instanceRepository.findByRequestNo("SE-9")).thenReturn(Optional.of(existing));

    assertThat(service.findByRequestNo("SE-9")).contains(ApprovalInstanceId.of(100L));
  }

  @Test
  @DisplayName("findByRequestNo 空键直接返回空，不访问仓储")
  void findByRequestNo_blank_returnsEmptyWithoutRepository() {
    assertThat(service.findByRequestNo("  ")).isEmpty();
    verifyNoInteractions(instanceRepository);
  }
}
