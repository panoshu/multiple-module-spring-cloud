package com.example.core.application.engine.step.handler;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.ApplicationSource;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.gateway.ApprovalIntegrationGateway;
import com.example.core.types.ApplicationId;
import com.example.core.types.BatchId;
import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.FormId;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.file.types.FileId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 通用审批提交步骤处理器测试：验证挂起返回与 requestNo 透传。 */
@DisplayName("ApprovalSubmissionHandler 审批提交步骤处理器测试")
@ExtendWith(MockitoExtension.class)
class ApprovalSubmissionHandlerTest {

  private static final ApplicationId APPLICATION_ID = new ApplicationId("app-001");
  private static final FileId PARSED_JSON_FILE_ID = new FileId("file-parsed-001");
  private static final String OPERATOR_ID = "user-001";
  private static final String INSTANCE_ID = "100";
  private static final String REQUEST_NO = "SE-9";

  @Mock
  private ApprovalIntegrationGateway approvalGateway;

  @InjectMocks
  private ApprovalSubmissionHandler handler;

  private BusinessApplication application;
  private BusinessMetaContext context;

  @BeforeEach
  void setUp() {
    BusinessContext businessContext = new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      CustomerNo.of("CUST-001"), "测试客户",
      ProductNo.of("PROD-001"), "测试产品",
      PlanNo.of("PLAN-001"), "测试计划",
      OperatingMode.TRUSTEE_ONLY, AccountManager.CJP
    );
    OperatorInfo operatorInfo = new OperatorInfo(
      Channel.NETAPP, OPERATOR_ID, "测试操作员", false
    );
    application = BusinessApplication.createFromForm(
      APPLICATION_ID, new ApplicationSource(new FormId("form-001"), BatchId.of("batch-001")),
      businessContext, operatorInfo, PARSED_JSON_FILE_ID, DefinitionVersion.initial()
    );
    context = BusinessMetaContext.of(businessContext);
    context.extensionFacts().put(BusinessMetaContext.REQUEST_NO_FACT, REQUEST_NO);
  }

  @Test
  @DisplayName("网关成功发起审批时应返回 SUSPEND_ASYNC_WAIT")
  void execute_whenGatewaySucceeds_shouldReturnSuspendAsyncWait() {
    when(approvalGateway.startApproval(any(BusinessApplication.class), eq(REQUEST_NO)))
      .thenReturn(INSTANCE_ID);

    StepExecutionStatus status = handler.execute(application, context);

    assertEquals(StepExecutionStatus.SUSPEND_ASYNC_WAIT, status);
  }

  @Test
  @DisplayName("网关抛出异常时应返回 FAILED")
  void execute_whenGatewayThrowsException_shouldReturnFailed() {
    when(approvalGateway.startApproval(any(BusinessApplication.class), eq(REQUEST_NO)))
      .thenThrow(new IllegalStateException("匹配审批流失败"));

    StepExecutionStatus status = handler.execute(application, context);

    assertEquals(StepExecutionStatus.FAILED, status);
  }

  @Test
  @DisplayName("把上下文中的 requestNo 透传给防腐层网关")
  void execute_passesRequestNoToGateway() {
    when(approvalGateway.startApproval(any(BusinessApplication.class), eq(REQUEST_NO)))
      .thenReturn(INSTANCE_ID);

    handler.execute(application, context);

    verify(approvalGateway).startApproval(application, REQUEST_NO);
  }

  @Test
  @DisplayName("handlerName 应返回 approvalSubmissionHandler")
  void handlerName_shouldReturnApprovalSubmissionHandler() {
    assertEquals("approvalSubmissionHandler", handler.handlerName());
  }
}
