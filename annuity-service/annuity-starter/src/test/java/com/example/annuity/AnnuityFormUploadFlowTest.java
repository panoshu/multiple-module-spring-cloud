package com.example.annuity;

import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.core.application.business.service.BusinessBatchAppService;
import com.example.core.application.engine.listener.ApplicationSpawnedListener;
import com.example.core.application.engine.listener.ExternalInteractionRequestedListener;
import com.example.core.application.engine.listener.StepAutoAdvanceListener;
import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.application.engine.step.service.BusinessFormAppService;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.BusinessFile;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.enums.status.FormStatus;
import com.example.core.domain.business.gateway.ParsedSubTaskReferenceGateway;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.business.repository.FormRepository;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.core.types.BatchId;
import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.FormId;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.file.api.FileAccessApi;
import com.example.file.api.FileTaskApi;
import com.example.core.types.FileRefId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 表单上传 → 解析回调 → 申请单裂变 → 明细摄入的真实主流程 E2E。
 *
 * <p>不直接构造 BusinessApplication：从 confirmUpload 开始，经 FormUploadedEvent、
 * Mock 文件解析事件、FileParsedEventListener 裂变，再由步骤管道完成员工明细摄入。</p>
 */
@SpringBootTest(classes = AnnuityApplication.class)
@ActiveProfiles("test")
@DisplayName("年金表单上传→解析→裂变→明细摄入 E2E")
class AnnuityFormUploadFlowTest {

  @Autowired
  private BusinessFormAppService formAppService;
  @Autowired
  private BusinessBatchAppService batchAppService;
  @Autowired
  private FormRepository formRepository;
  @Autowired
  private ApplicationRepository applicationRepository;
  @Autowired
  private ParsedSubTaskReferenceGateway parsedSubTaskGateway;
  @Autowired
  private AnnuityEmployeeBatchRepository employeeBatchRepository;
  @Autowired
  private com.example.annuity.domain.repository.AnnuityApplicationExtensionStore extensionStore;
  @Autowired
  private FlowOrchestrationService orchestrationService;

  @MockitoBean
  private StepAutoAdvanceListener stepAutoAdvanceListener;
  @MockitoBean
  private ApplicationSpawnedListener applicationSpawnedListener;
  @MockitoBean
  private ExternalInteractionRequestedListener externalInteractionRequestedListener;

  @MockitoBean
  private FileTaskApi fileTaskApi;
  @MockitoBean
  private FileAccessApi fileAccessApi;
  @MockitoBean
  private ApprovalFlowApi approvalFlowApi;
  @MockitoBean
  private ApprovalInstanceApi approvalInstanceApi;

  @Test
  @DisplayName("confirmUpload 后自动裂变申请单，并可完成员工明细摄入")
  void confirmUpload_spawnsApplicationAndIngestsDetails() {
    BusinessContext context = buildContext();
    OperatorInfo operator = buildOperator();
    BusinessBatch batch = batchAppService.createBatch(context, operator);

    FormId formId = new FormId("FORM-FLOW-1");
    BusinessForm form = BusinessForm.create(formId, batch.id(), context, operator, null);
    formRepository.save(form);
    assertThat(form.formStatus()).isEqualTo(FormStatus.WAITING_UPLOAD);

    BusinessFile file = new BusinessFile(new FileRefId("FILE-FLOW-1"), "form.xlsx", "xlsx", 1024L);
    formAppService.confirmUpload(formId, file);

    BusinessForm parsedForm = formRepository.loadOrThrow(formId);
    assertThat(parsedForm.formStatus()).isEqualTo(FormStatus.PARSED);

    List<BusinessApplication> applications = applicationRepository.findByBatchId(batch.id());
    assertThat(applications).hasSize(1);
    BusinessApplication app = applications.get(0);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.FORM_DETAIL_INGESTION);
    assertThat(parsedSubTaskGateway.load(app.id())).contains("MOCK-SUBTASK-FILE-FLOW-1");

    // 年金业务扩展字段在真实链路由上传/解析数据注入；示例测试补一份默认值后推进
    extensionStore.save(app.id(), new com.example.annuity.domain.extension.AnnuityApplicationExtension(
      app.businessContext().businessType(), "NEW", 20000L, false));

    orchestrationService.advanceStep(app.id());

    AnnuityEmployeeBatch employeeBatch = employeeBatchRepository.findByApplicationId(app.id())
      .orElseThrow();
    assertThat(employeeBatch.details()).hasSize(2);
    assertThat(employeeBatch.details())
      .extracting("employeeName")
      .containsExactlyInAnyOrder("张三", "李四");
  }

  private BusinessContext buildContext() {
    return new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      CustomerNo.of("C-FLOW-001"), "流程客户",
      ProductNo.of("P-FLOW-001"), "流程产品",
      PlanNo.of("PL-FLOW-001"), "流程方案",
      OperatingMode.TRUSTEE_ONLY, AccountManager.CJP
    );
  }

  private OperatorInfo buildOperator() {
    return new OperatorInfo(Channel.NETAPP, "U-FLOW-001", "流程操作人", false);
  }
}
