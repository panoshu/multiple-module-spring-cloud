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

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 审批提交步骤处理器测试：验证登记意图（批次 31 改为事务外派发）。 */
@DisplayName("ApprovalSubmissionHandler 审批登记意图测试")
class ApprovalSubmissionHandlerTest {

  private static final ApplicationId APPLICATION_ID = new ApplicationId("app-001");
  private static final FileId PARSED_JSON_FILE_ID = new FileId("file-parsed-001");
  private static final String OPERATOR_ID = "user-001";
  private static final String REQUEST_NO = "SE-9";

  private ApprovalSubmissionHandler handler;
  private BusinessApplication application;
  private BusinessMetaContext context;

  @BeforeEach
  void setUp() {
    handler = new ApprovalSubmissionHandler();
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
      businessContext, operatorInfo, DefinitionVersion.initial()
    );
    context = BusinessMetaContext.of(businessContext);
    context.extensionFacts().put(BusinessMetaContext.REQUEST_NO_FACT, REQUEST_NO);
  }

  @Test
  @DisplayName("登记挂起意图返回 SUSPEND_ASYNC_WAIT")
  void execute_registersIntentAndSuspends() {
    StepExecutionStatus status = handler.execute(application, context);

    assertEquals(StepExecutionStatus.SUSPEND_ASYNC_WAIT, status);
  }

  @Test
  @DisplayName("handlerName 应返回 approvalSubmissionHandler")
  void handlerName_shouldReturnApprovalSubmissionHandler() {
    assertEquals("approvalSubmissionHandler", handler.handlerName());
  }
}
