package com.example.core.application.engine.dispatch;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.engine.aggregate.root.ExternalInteraction;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.gateway.ApprovalIntegrationGateway;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** APPROVAL 派发处理器（阶段 B 收尾，批次 31）。 */
@DisplayName("ApprovalDispatchHandler 事务外启动审批")
class ApprovalDispatchHandlerTest {

  private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 13, 10, 0);
  private static final ApplicationId APP_ID = new ApplicationId("APP-1");

  private ApplicationRepository applicationRepository;
  private ApprovalIntegrationGateway approvalGateway;
  private ApprovalDispatchHandler handler;

  @BeforeEach
  void setUp() {
    applicationRepository = mock(ApplicationRepository.class);
    approvalGateway = mock(ApprovalIntegrationGateway.class);
    handler = new ApprovalDispatchHandler(applicationRepository, approvalGateway);
  }

  @Test
  @DisplayName("supportedType 为 APPROVAL")
  void supportedType_isApproval() {
    assertThat(handler.supportedType()).isEqualTo(InteractionType.APPROVAL);
  }

  @Test
  @DisplayName("加载申请单并携带 requestNo 启动审批，返回 instanceId")
  void dispatch_startsApprovalWithRequestNo() {
    BusinessApplication app = mock(BusinessApplication.class);
    when(applicationRepository.loadOrThrow(APP_ID)).thenReturn(app);
    when(approvalGateway.startApproval(app, "SE-9")).thenReturn("AI-9");
    ExternalInteraction interaction = interaction();

    assertThat(handler.dispatch(interaction)).isEqualTo("AI-9");
  }

  @Test
  @DisplayName("网关返回 null 时透传 null（保持 DISPATCHING 待对账）")
  void dispatch_returnsNullWhenGatewayReturnsNull() {
    BusinessApplication app = mock(BusinessApplication.class);
    when(applicationRepository.loadOrThrow(APP_ID)).thenReturn(app);
    when(approvalGateway.startApproval(app, "SE-9")).thenReturn(null);
    ExternalInteraction interaction = interaction();

    assertThat(handler.dispatch(interaction)).isNull();
  }

  private ExternalInteraction interaction() {
    return ExternalInteraction.reconstitute(
      ExternalInteractionId.of(1L), "U-C", "U-U", NOW.minusDays(1), NOW, Version.of(1L),
      APP_ID, StepExecutionId.of(9L), InteractionType.APPROVAL,
      "SE-9", null, ExternalInteractionStatus.DISPATCHING,
      null, 0, null, null, null);
  }
}
