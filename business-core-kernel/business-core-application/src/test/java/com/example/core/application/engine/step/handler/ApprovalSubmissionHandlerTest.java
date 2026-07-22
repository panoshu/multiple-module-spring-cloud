package com.example.core.application.engine.step.handler;

import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.approval.api.dto.ApprovalFlowDTO;
import com.example.approval.api.request.MatchApprovalFlowRequest;
import com.example.approval.api.request.StartApprovalRequest;
import com.example.approval.api.response.ApprovalInstanceIdResponse;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.ApprovalInstanceId;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.PlanNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import com.example.shared.web.core.api.ApiResult;
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
 * <li>成功匹配审批流并启动实例返回 SUSPEND_ASYNC_WAIT</li>
 * <li>匹配审批流失败返回 FAILED</li>
 * <li>启动审批实例失败返回 FAILED</li>
 * <li>API 抛出异常返回 FAILED</li>
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
  private static final ApprovalFlowId FLOW_ID = ApprovalFlowId.of(1L);
  private static final ApprovalInstanceId INSTANCE_ID = ApprovalInstanceId.of(100L);

  @Mock
  private ApprovalFlowApi approvalFlowApi;

  @Mock
  private ApprovalInstanceApi approvalInstanceApi;

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
  @DisplayName("成功匹配审批流并启动实例时应返回 SUSPEND_ASYNC_WAIT")
  void execute_whenMatchAndStartSucceed_shouldReturnSuspendAsyncWait() {
    // given: 审批流匹配成功 + 审批实例启动成功
    ApprovalFlowDTO flowDTO = new ApprovalFlowDTO(
      FLOW_ID, "测试审批流", BusinessType.ACC_PLAN_CREATE.name(),
      "ACTIVE", 1, null, null,
      "user-001", null, null
    );
    when(approvalFlowApi.match(any(MatchApprovalFlowRequest.class)))
      .thenReturn(ApiResult.success(flowDTO));
    when(approvalInstanceApi.start(any(StartApprovalRequest.class)))
      .thenReturn(ApiResult.success(new ApprovalInstanceIdResponse(INSTANCE_ID)));

    // when
    StepExecutionStatus status = handler.execute(application, context);

    // then
    assertEquals(StepExecutionStatus.SUSPEND_ASYNC_WAIT, status,
      "成功匹配并启动审批后应返回 SUSPEND_ASYNC_WAIT 挂起引擎");
  }

  @Test
  @DisplayName("匹配审批流返回失败结果时应返回 FAILED")
  void execute_whenMatchReturnsFailure_shouldReturnFailed() {
    // given: 审批流匹配失败
    when(approvalFlowApi.match(any(MatchApprovalFlowRequest.class)))
      .thenReturn(ApiResult.failure("404", "未找到匹配的审批流"));

    // when
    StepExecutionStatus status = handler.execute(application, context);

    // then
    assertEquals(StepExecutionStatus.FAILED, status,
      "匹配审批流失败时应返回 FAILED 阻断流程");
  }

  @Test
  @DisplayName("匹配审批流返回 null 时应返回 FAILED")
  void execute_whenMatchReturnsNull_shouldReturnFailed() {
    // given: 审批流匹配返回 null
    when(approvalFlowApi.match(any(MatchApprovalFlowRequest.class)))
      .thenReturn(null);

    // when
    StepExecutionStatus status = handler.execute(application, context);

    // then
    assertEquals(StepExecutionStatus.FAILED, status,
      "匹配审批流返回 null 时应返回 FAILED");
  }

  @Test
  @DisplayName("匹配审批流返回成功但 data 为 null 时应返回 FAILED")
  void execute_whenMatchReturnsSuccessButNullData_shouldReturnFailed() {
    // given: 审批流匹配返回成功但 data 为 null
    when(approvalFlowApi.match(any(MatchApprovalFlowRequest.class)))
      .thenReturn(ApiResult.success(null));

    // when
    StepExecutionStatus status = handler.execute(application, context);

    // then
    assertEquals(StepExecutionStatus.FAILED, status,
      "匹配审批流返回成功但 data 为 null 时应返回 FAILED");
  }

  @Test
  @DisplayName("匹配 API 抛出异常时应返回 FAILED")
  void execute_whenMatchThrowsException_shouldReturnFailed() {
    // given: 匹配 API 抛出异常
    when(approvalFlowApi.match(any(MatchApprovalFlowRequest.class)))
      .thenThrow(new RuntimeException("approval-service 网络异常"));

    // when
    StepExecutionStatus status = handler.execute(application, context);

    // then
    assertEquals(StepExecutionStatus.FAILED, status,
      "匹配 API 抛出异常时应返回 FAILED 而不是向上抛出");
  }

  @Test
  @DisplayName("匹配成功但启动审批实例失败时应返回 FAILED")
  void execute_whenMatchSucceedsButStartFails_shouldReturnFailed() {
    // given: 审批流匹配成功，但启动实例失败
    ApprovalFlowDTO flowDTO = new ApprovalFlowDTO(
      FLOW_ID, "测试审批流", BusinessType.ACC_PLAN_CREATE.name(),
      "ACTIVE", 1, null, null,
      "user-001", null, null
    );
    when(approvalFlowApi.match(any(MatchApprovalFlowRequest.class)))
      .thenReturn(ApiResult.success(flowDTO));
    when(approvalInstanceApi.start(any(StartApprovalRequest.class)))
      .thenReturn(ApiResult.failure("500", "启动审批实例失败"));

    // when
    StepExecutionStatus status = handler.execute(application, context);

    // then
    assertEquals(StepExecutionStatus.FAILED, status,
      "匹配成功但启动实例失败时应返回 FAILED");
  }

  @Test
  @DisplayName("匹配成功但启动 API 抛出异常时应返回 FAILED")
  void execute_whenMatchSucceedsButStartThrowsException_shouldReturnFailed() {
    // given: 审批流匹配成功，但启动 API 抛出异常
    ApprovalFlowDTO flowDTO = new ApprovalFlowDTO(
      FLOW_ID, "测试审批流", BusinessType.ACC_PLAN_CREATE.name(),
      "ACTIVE", 1, null, null,
      "user-001", null, null
    );
    when(approvalFlowApi.match(any(MatchApprovalFlowRequest.class)))
      .thenReturn(ApiResult.success(flowDTO));
    when(approvalInstanceApi.start(any(StartApprovalRequest.class)))
      .thenThrow(new RuntimeException("approval-service 网络异常"));

    // when
    StepExecutionStatus status = handler.execute(application, context);

    // then
    assertEquals(StepExecutionStatus.FAILED, status,
      "匹配成功但启动 API 抛出异常时应返回 FAILED");
  }

  @Test
  @DisplayName("handlerName 应返回 approvalSubmissionHandler")
  void handlerName_shouldReturnApprovalSubmissionHandler() {
    assertEquals("approvalSubmissionHandler", handler.handlerName(),
      "handlerName 应返回配置中心约定的 bean 标识");
  }
}
