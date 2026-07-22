package com.example.core.application.handler;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.file.api.FileTaskApi;
import com.example.file.api.request.UploadFileRequest;
import com.example.file.api.response.FileTaskIdResponse;
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
 * FileServiceParseHandler 单元测试
 * <p>
 * 验证通用文件解析步骤处理器的核心行为：
 * <ul>
 * <li>成功派发解析任务返回 SUSPEND_ASYNC_WAIT</li>
 * <li>API 返回失败结果返回 FAILED</li>
 * <li>API 抛出异常返回 FAILED</li>
 * </ul>
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 12:14
 */
@DisplayName("FileServiceParseHandler 文件解析步骤处理器测试")
@ExtendWith(MockitoExtension.class)
class FileServiceParseHandlerTest {

  private static final ApplicationId APPLICATION_ID = new ApplicationId("app-001");
  private static final FileId PARSED_JSON_FILE_ID = new FileId("file-parsed-001");
  private static final UserNo OPERATOR_ID = UserNo.of("user-001");
  private static final String FILE_TASK_ID = "task-001";

  @Mock
  private FileTaskApi fileTaskApi;

  @InjectMocks
  private FileServiceParseHandler handler;

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
  @DisplayName("成功派发解析任务时应返回 SUSPEND_ASYNC_WAIT")
  void execute_whenApiSucceeds_shouldReturnSuspendAsyncWait() {
    // given: file-service 返回成功
    ApiResult<FileTaskIdResponse> successResult = ApiResult.success(new FileTaskIdResponse(FILE_TASK_ID));
    when(fileTaskApi.upload(any(UploadFileRequest.class))).thenReturn(successResult);

    // when
    StepExecutionStatus status = handler.execute(application, context);

    // then
    assertEquals(StepExecutionStatus.SUSPEND_ASYNC_WAIT, status,
      "成功派发解析任务后应返回 SUSPEND_ASYNC_WAIT 挂起引擎");
  }

  @Test
  @DisplayName("API 返回失败结果时应返回 FAILED")
  void execute_whenApiReturnsFailure_shouldReturnFailed() {
    // given: file-service 返回失败
    ApiResult<FileTaskIdResponse> failureResult = ApiResult.failure("500", "文件服务内部错误");
    when(fileTaskApi.upload(any(UploadFileRequest.class))).thenReturn(failureResult);

    // when
    StepExecutionStatus status = handler.execute(application, context);

    // then
    assertEquals(StepExecutionStatus.FAILED, status,
      "API 返回失败结果时应返回 FAILED 阻断流程");
  }

  @Test
  @DisplayName("API 返回 null 时应返回 FAILED")
  void execute_whenApiReturnsNull_shouldReturnFailed() {
    // given: file-service 返回 null
    when(fileTaskApi.upload(any(UploadFileRequest.class))).thenReturn(null);

    // when
    StepExecutionStatus status = handler.execute(application, context);

    // then
    assertEquals(StepExecutionStatus.FAILED, status,
      "API 返回 null 时应返回 FAILED 阻断流程");
  }

  @Test
  @DisplayName("API 返回成功但 data 为 null 时应返回 FAILED")
  void execute_whenApiReturnsSuccessButNullData_shouldReturnFailed() {
    // given: file-service 返回成功但 data 为 null
    ApiResult<FileTaskIdResponse> nullDataResult = ApiResult.success(null);
    when(fileTaskApi.upload(any(UploadFileRequest.class))).thenReturn(nullDataResult);

    // when
    StepExecutionStatus status = handler.execute(application, context);

    // then
    assertEquals(StepExecutionStatus.FAILED, status,
      "API 返回成功但 data 为 null 时应返回 FAILED");
  }

  @Test
  @DisplayName("API 抛出异常时应返回 FAILED")
  void execute_whenApiThrowsException_shouldReturnFailed() {
    // given: file-service 调用抛出异常
    when(fileTaskApi.upload(any(UploadFileRequest.class)))
      .thenThrow(new RuntimeException("网络连接失败"));

    // when
    StepExecutionStatus status = handler.execute(application, context);

    // then
    assertEquals(StepExecutionStatus.FAILED, status,
      "API 抛出异常时应返回 FAILED 而不是向上抛出");
  }

  @Test
  @DisplayName("handlerName 应返回 fileServiceParseHandler")
  void handlerName_shouldReturnFileServiceParseHandler() {
    assertEquals("fileServiceParseHandler", handler.handlerName(),
      "handlerName 应返回配置中心约定的 bean 标识");
  }
}
