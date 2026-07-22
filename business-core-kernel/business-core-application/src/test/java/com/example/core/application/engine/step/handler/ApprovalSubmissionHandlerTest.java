package com.example.core.application.engine.step.handler;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.gateway.ApprovalIntegrationGateway;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.PlanNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * ApprovalSubmissionHandler 单元测试
 * <p>
 * 验证通用审批提交步骤处理器的核心行为：
 * <ul>
 * <li>通过防腐层网关成功发起审批返回 SUSPEND_ASYNC_WAIT</li>
 * <li>网关抛出异常返回 FAILED</li>
 * </ul>
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 12:14
 */
@DisplayName("ApprovalSubmissionHandler 审批提交步骤处理器测试")
@ExtendWith(MockitoExtension.class)
class ApprovalSubmissionHandlerTest {

  private static final ApplicationId APPLICATION_ID = new ApplicationId("app-001");
  private static final FileId PARSED_JSON_FILE_ID = new FileId("file-parsed-001");
  private static final UserNo OPERATOR_ID = UserNo.of("user-001");
  private static final String INSTANCE_ID = "100";

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
      CustomerNo.of("CUST-001"),
      "测试客户",
      ProductNo.of("PROD-001"),
      "测试产品",
      PlanNo.of("PLAN-001"),
      "测试计划",
      OperationModel.Single_Trustee,
      AccountManager.CJP
    );
    OperatorInfo operatorInfo = new OperatorInfo(
      AnnuityChannel.NETAPP, OPERATOR_ID, "测试操作员", false
    );
    application = BusinessApplication.createFromForm(
      APPLICATION_ID, businessContext, operatorInfo, PARSED_JSON_FILE_ID
    );
    context = BusinessMetaContext.of(businessContext);
  }

  @Test
  @DisplayName("网关成功发起审批时应返回 SUSPEND_ASYNC_WAIT")
  void execute_whenGatewaySucceeds_shouldReturnSuspendAsyncWait() {
    // given: 防腐层网关成功启动审批实例
    when(approvalGateway.startApproval(any(BusinessApplication.class))).thenReturn(INSTANCE_ID);

    // when
    StepExecutionStatus status = handler.execute(application, context);

    // then
    assertEquals(StepExecutionStatus.SUSPEND_ASYNC_WAIT, status,
      "网关成功发起审批后应返回 SUSPEND_ASYNC_WAIT 挂起引擎");
  }

  @Test
  @DisplayName("网关抛出异常时应返回 FAILED")
  void execute_whenGatewayThrowsException_shouldReturnFailed() {
    // given: 防腐层网关抛出异常（如匹配审批流失败、启动实例失败）
    when(approvalGateway.startApproval(any(BusinessApplication.class)))
      .thenThrow(new IllegalStateException("匹配审批流失败"));

    // when
    StepExecutionStatus status = handler.execute(application, context);

    // then
    assertEquals(StepExecutionStatus.FAILED, status,
      "网关抛出异常时应返回 FAILED 阻断流程");
  }

  @Test
  @DisplayName("handlerName 应返回 approvalSubmissionHandler")
  void handlerName_shouldReturnApprovalSubmissionHandler() {
    assertEquals("approvalSubmissionHandler", handler.handlerName(),
      "handlerName 应返回配置中心约定的 bean 标识");
  }
}
