package com.example.core.application.engine.service;

import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.gateway.ApprovalIntegrationGateway;
import com.example.core.domain.engine.repository.ExternalInteractionRepository;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExternalInteractionRecoveryService 恢复动作")
class ExternalInteractionRecoveryServiceTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 10, 0);

  @Mock
  private ExternalInteractionRepository repository;
  @Mock
  private ApprovalIntegrationGateway approvalGateway;

  private ExternalInteractionRecoveryService service;

  @BeforeEach
  void setUp() {
    service = new ExternalInteractionRecoveryService(repository, approvalGateway);
  }

  @Test
  @DisplayName("认领待派发协作：PENDING_DISPATCH → DISPATCHING 并逐条保存")
  void claimPendingDispatch_marksDispatchingAndSaves() {
    ExternalInteraction pending = reconstitute(1L, ExternalInteractionStatus.PENDING_DISPATCH, null);
    when(repository.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH)).thenReturn(List.of(pending));

    assertThat(service.claimPendingDispatch()).isEqualTo(1);

    assertThat(pending.status()).isEqualTo(ExternalInteractionStatus.DISPATCHING);
    verify(repository).save(pending);
  }

  @Test
  @DisplayName("无待派发协作时不保存")
  void claimPendingDispatch_emptyNoop() {
    when(repository.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH)).thenReturn(List.of());

    assertThat(service.claimPendingDispatch()).isZero();
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("认领单条失败隔离，不影响同批其他协作")
  void claimPendingDispatch_isolatesSingleFailure() {
    ExternalInteraction first = reconstitute(1L, ExternalInteractionStatus.PENDING_DISPATCH, null);
    ExternalInteraction second = reconstitute(2L, ExternalInteractionStatus.DISPATCHING, null);
    when(repository.findByStatus(ExternalInteractionStatus.PENDING_DISPATCH)).thenReturn(List.of(first, second));

    assertThat(service.claimPendingDispatch()).isEqualTo(1);

    assertThat(first.status()).isEqualTo(ExternalInteractionStatus.DISPATCHING);
    assertThat(second.status()).isEqualTo(ExternalInteractionStatus.DISPATCHING);
    verify(repository).save(first);
  }

  @Test
  @DisplayName("回收滞留 DISPATCHING：结果未知进入 UNKNOWN")
  void reclaimStaleDispatching_marksUnknown() {
    ExternalInteraction stale = reconstitute(2L, ExternalInteractionStatus.DISPATCHING, null);
    LocalDateTime before = NOW.minusMinutes(30);
    when(repository.findStaleDispatching(before)).thenReturn(List.of(stale));

    assertThat(service.reclaimStaleDispatching(before)).isEqualTo(1);

    assertThat(stale.status()).isEqualTo(ExternalInteractionStatus.UNKNOWN);
    assertThat(stale.lastError()).isNotBlank();
    verify(repository).save(stale);
  }

  @Test
  @DisplayName("处理到期重试：RETRY_WAIT → DISPATCHING 重派")
  void processDueRetries_dispatchesAgain() {
    ExternalInteraction retry = reconstitute(3L, ExternalInteractionStatus.RETRY_WAIT, null);
    when(repository.findPendingRetry(NOW)).thenReturn(List.of(retry));

    assertThat(service.processDueRetries(NOW)).isEqualTo(1);

    assertThat(retry.status()).isEqualTo(ExternalInteractionStatus.DISPATCHING);
    verify(repository).save(retry);
  }

  @Test
  @DisplayName("UNKNOWN 对账：审批通过确认成功")
  void reconcileUnknown_confirmsApproved() {
    ExternalInteraction unknown = reconstitute(4L, ExternalInteractionStatus.UNKNOWN, "AI-1");
    when(repository.findByStatus(ExternalInteractionStatus.UNKNOWN)).thenReturn(List.of(unknown));
    when(approvalGateway.queryApprovalStatus("AI-1")).thenReturn("APPROVED");

    assertThat(service.reconcileUnknown()).isEqualTo(1);

    assertThat(unknown.status()).isEqualTo(ExternalInteractionStatus.SUCCEEDED);
    assertThat(unknown.resultRef()).isNotBlank();
    verify(repository).save(unknown);
  }

  @Test
  @DisplayName("UNKNOWN 对账：审批驳回确认失败")
  void reconcileUnknown_confirmsRejected() {
    ExternalInteraction unknown = reconstitute(4L, ExternalInteractionStatus.UNKNOWN, "AI-1");
    when(repository.findByStatus(ExternalInteractionStatus.UNKNOWN)).thenReturn(List.of(unknown));
    when(approvalGateway.queryApprovalStatus("AI-1")).thenReturn("REJECTED");

    assertThat(service.reconcileUnknown()).isEqualTo(1);

    assertThat(unknown.status()).isEqualTo(ExternalInteractionStatus.FAILED);
    verify(repository).save(unknown);
  }

  @Test
  @DisplayName("UNKNOWN 无外部任务 ID 时跳过，不盲目重派也不查外部")
  void reconcileUnknown_skipsWithoutExternalTaskId() {
    ExternalInteraction unknown = reconstitute(5L, ExternalInteractionStatus.UNKNOWN, null);
    when(repository.findByStatus(ExternalInteractionStatus.UNKNOWN)).thenReturn(List.of(unknown));

    assertThat(service.reconcileUnknown()).isZero();

    assertThat(unknown.status()).isEqualTo(ExternalInteractionStatus.UNKNOWN);
    verifyNoInteractions(approvalGateway);
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("UNKNOWN 对账外部查询失败时隔离并保留 UNKNOWN")
  void reconcileUnknown_isolatesGatewayFailure() {
    ExternalInteraction unknown = reconstitute(6L, ExternalInteractionStatus.UNKNOWN, "AI-2");
    when(repository.findByStatus(ExternalInteractionStatus.UNKNOWN)).thenReturn(List.of(unknown));
    when(approvalGateway.queryApprovalStatus("AI-2")).thenThrow(new RuntimeException("timeout"));

    assertThat(service.reconcileUnknown()).isZero();

    assertThat(unknown.status()).isEqualTo(ExternalInteractionStatus.UNKNOWN);
    verify(repository, never()).save(any());
  }

  @Test
  @DisplayName("四类动作均只依赖 Repository 查询契约")
  void recoveryActions_areQueryDriven() {
    when(repository.findByStatus(any())).thenReturn(List.of());
    when(repository.findStaleDispatching(any())).thenReturn(List.of());
    when(repository.findPendingRetry(any())).thenReturn(List.of());

    assertThat(service.claimPendingDispatch()).isZero();
    assertThat(service.reclaimStaleDispatching(NOW)).isZero();
    assertThat(service.processDueRetries(NOW)).isZero();
    assertThat(service.reconcileUnknown()).isZero();
  }

  private ExternalInteraction reconstitute(long id, ExternalInteractionStatus status, String externalTaskId) {
    return ExternalInteraction.reconstitute(
      ExternalInteractionId.of(id), "U-C", "U-U", NOW.minusDays(1), NOW, Version.of(1L),
      new ApplicationId("APP-1"), StepExecutionId.of(9L), InteractionType.APPROVAL,
      "SE-9", externalTaskId, status,
      null, 0, null, null, null);
  }
}
