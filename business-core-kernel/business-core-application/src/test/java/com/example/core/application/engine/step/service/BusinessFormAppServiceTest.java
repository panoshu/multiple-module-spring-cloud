package com.example.core.application.engine.step.service;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.ParsedPlanResult;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.domain.business.gateway.ParsedFileReferenceGateway;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.business.repository.BatchRepository;
import com.example.core.domain.business.repository.FormRepository;
import com.example.core.domain.engine.aggregate.valueobject.BusinessTypeCode;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.BatchMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InstanceGranularity;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.StartMode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.TaskType;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.example.core.domain.engine.gateway.FileIntegrationGateway;
import com.example.core.domain.engine.gateway.ProcessDefinitionGateway;
import com.example.core.types.ApplicationId;
import com.example.core.types.BatchId;
import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.FormId;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.file.types.FileId;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.contract.IdService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BusinessFormAppService 测试")
class BusinessFormAppServiceTest {

  private static final FormId FORM_ID = new FormId("FORM001");

  private FormRepository formRepository;
  private ApplicationRepository applicationRepository;
  private BatchRepository batchRepository;
  private BusinessConfigGateway configGateway;
  private FileIntegrationGateway fileIntegrationGateway;
  private IdService idService;
  private ProcessDefinitionGateway processDefinitionGateway;
  private ParsedFileReferenceGateway parsedFileReferenceGateway;
  private BusinessFormAppService appService;

  @BeforeEach
  void setUp() {
    formRepository = mock(FormRepository.class);
    applicationRepository = mock(ApplicationRepository.class);
    batchRepository = mock(BatchRepository.class);
    configGateway = mock(BusinessConfigGateway.class);
    fileIntegrationGateway = mock(FileIntegrationGateway.class);
    idService = mock(IdService.class);
    processDefinitionGateway = mock(ProcessDefinitionGateway.class);
    parsedFileReferenceGateway = mock(ParsedFileReferenceGateway.class);
    when(processDefinitionGateway.loadLatest(any()))
      .thenReturn(Optional.of(definitionWith(BatchMode.REQUIRED)));
    appService = new BusinessFormAppService(
      formRepository, applicationRepository, batchRepository, configGateway,
      fileIntegrationGateway, idService, processDefinitionGateway, parsedFileReferenceGateway
    );
  }

  @Test
  void should_apply_upload_token_via_gateway() {
    when(fileIntegrationGateway.applyUploadToken("127.0.0.1", "U001", 1024L))
      .thenReturn("token-abc-123");

    String token = appService.applyUploadToken("127.0.0.1", "U001", 1024L);

    assertThat(token).isEqualTo("token-abc-123");
    verify(fileIntegrationGateway).applyUploadToken("127.0.0.1", "U001", 1024L);
  }

  @Test
  void should_delete_form_and_save() {
    BusinessForm form = mock(BusinessForm.class);
    when(formRepository.loadOrThrow(FORM_ID)).thenReturn(form);
    when(form.domainEvents()).thenReturn(List.of());

    appService.deleteForm(FORM_ID);

    verify(form).markAsDeleted();
    verify(formRepository).save(form);
  }

  @Test
  void should_get_form_status() {
    BusinessForm form = mock(BusinessForm.class);
    when(formRepository.loadOrThrow(FORM_ID)).thenReturn(form);

    BusinessForm result = appService.getFormStatus(FORM_ID);

    assertThat(result).isSameAs(form);
    verify(formRepository).loadOrThrow(FORM_ID);
  }

  @Test
  @DisplayName("解析结果按 applicationId 保存明细 JSON 文件引用（§6.2，批次 37c-1）")
  void handleParsingResult_savesParsedFileReferences() {
    BusinessBatch batch = newBatch();
    BusinessForm form = mockForm(batch.id());
    when(formRepository.loadOrThrow(FORM_ID)).thenReturn(form);
    when(batchRepository.loadOrThrow(batch.id())).thenReturn(batch);
    when(idService.nextId(ApplicationId.class, ""))
      .thenReturn(new ApplicationId("APP-1"), new ApplicationId("APP-2"));
    List<ParsedPlanResult> results = List.of(
      new ParsedPlanResult(PlanNo.of("PLAN-1"), new FileId("JSON-1"), 1),
      new ParsedPlanResult(PlanNo.of("PLAN-2"), new FileId("JSON-2"), 2)
    );

    appService.handleParsingResult(FORM_ID, results);

    verify(parsedFileReferenceGateway).save(new ApplicationId("APP-1"), new FileId("JSON-1"));
    verify(parsedFileReferenceGateway).save(new ApplicationId("APP-2"), new FileId("JSON-2"));
  }

  @Test
  @DisplayName("解析结果在同一批次和表单下创建多个申请")
  void handleParsingResult_createsApplicationsWithinSameBatch() {
    BusinessBatch batch = newBatch();
    BusinessForm form = mockForm(batch.id());
    when(formRepository.loadOrThrow(FORM_ID)).thenReturn(form);
    when(batchRepository.loadOrThrow(batch.id())).thenReturn(batch);
    when(idService.nextId(ApplicationId.class, ""))
      .thenReturn(new ApplicationId("APP-1"), new ApplicationId("APP-2"));
    List<ParsedPlanResult> results = List.of(
      new ParsedPlanResult(PlanNo.of("PLAN-1"), new FileId("JSON-1"), 1),
      new ParsedPlanResult(PlanNo.of("PLAN-2"), new FileId("JSON-2"), 2)
    );

    appService.handleParsingResult(FORM_ID, results);

    ArgumentCaptor<BusinessApplication> captor = ArgumentCaptor.forClass(BusinessApplication.class);
    verify(applicationRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
      .allSatisfy(app -> {
        assertThat(app.formId()).isEqualTo(FORM_ID);
        assertThat(app.batchId()).isEqualTo(batch.id());
        assertThat(app.definitionVersion()).isEqualTo(DefinitionVersion.initial());
      });
    assertThat(batch.totalApplicationCount()).isEqualTo(2);
    assertThat(batch.status()).isEqualTo(BatchStatus.PROCESSING);
    verify(batchRepository, times(1)).save(batch);
    verify(formRepository).save(form);
  }

  @Test
  @DisplayName("batchMode=NONE 时不加载批次，申请不携带批次归属")
  void handleParsingResult_createsApplicationsWithoutBatchWhenModeNone() {
    when(processDefinitionGateway.loadLatest(any()))
      .thenReturn(Optional.of(definitionWith(BatchMode.NONE)));
    BusinessForm form = mockForm(BatchId.of("B-UNUSED"));
    when(formRepository.loadOrThrow(FORM_ID)).thenReturn(form);
    when(idService.nextId(ApplicationId.class, "")).thenReturn(new ApplicationId("APP-1"));
    List<ParsedPlanResult> results = List.of(
      new ParsedPlanResult(PlanNo.of("PLAN-1"), new FileId("JSON-1"), 1)
    );

    appService.handleParsingResult(FORM_ID, results);

    verify(batchRepository, never()).loadOrThrow(any());
    verify(batchRepository, never()).save(any());
    ArgumentCaptor<BusinessApplication> captor = ArgumentCaptor.forClass(BusinessApplication.class);
    verify(applicationRepository).save(captor.capture());
    assertThat(captor.getValue().batchId()).isNull();
    assertThat(captor.getValue().definitionVersion()).isEqualTo(DefinitionVersion.initial());
  }

  @Test
  @DisplayName("空解析结果不创建申请也不更新批次")
  void handleParsingResult_doesNotChangeBatchWhenNoApplicationCreated() {
    BusinessBatch batch = newBatch();
    BusinessForm form = mockForm(batch.id());
    when(formRepository.loadOrThrow(FORM_ID)).thenReturn(form);
    when(batchRepository.loadOrThrow(batch.id())).thenReturn(batch);

    appService.handleParsingResult(FORM_ID, List.of());

    verify(formRepository).save(form);
    verify(applicationRepository, never()).save(any());
    verify(batchRepository, never()).save(any());
    assertThat(batch.status()).isEqualTo(BatchStatus.CREATED);
    assertThat(batch.totalApplicationCount()).isZero();
  }

  @Test
  @DisplayName("终态批次拒绝接收解析产生的新申请")
  void handleParsingResult_rejectsTerminalBatch() {
    BusinessBatch batch = newBatch();
    batch.receiveApplications(1, operator());
    batch.registerSuccess(operator());
    BusinessForm form = mockForm(batch.id());
    when(formRepository.loadOrThrow(FORM_ID)).thenReturn(form);
    when(batchRepository.loadOrThrow(batch.id())).thenReturn(batch);
    List<ParsedPlanResult> results = List.of(
      new ParsedPlanResult(PlanNo.of("PLAN-1"), new FileId("JSON-1"), 1)
    );

    assertThatThrownBy(() -> appService.handleParsingResult(FORM_ID, results))
      .isInstanceOf(DomainException.class);

    verify(applicationRepository, never()).save(any());
    verify(batchRepository, never()).save(any());
  }

  @Test
  @DisplayName("批次不存在时不保存表单或申请")
  void handleParsingResult_rejectsMissingBatch() {
    BatchId batchId = BatchId.of("B-MISSING");
    BusinessForm form = mockForm(batchId);
    when(formRepository.loadOrThrow(FORM_ID)).thenReturn(form);
    when(batchRepository.loadOrThrow(batchId))
      .thenThrow(new DomainException(CoreDomainErrorCode.INVALID_DATA));
    List<ParsedPlanResult> results = List.of(
      new ParsedPlanResult(PlanNo.of("PLAN-1"), new FileId("JSON-1"), 1)
    );

    assertThatThrownBy(() -> appService.handleParsingResult(FORM_ID, results))
      .isInstanceOf(DomainException.class);

    verify(formRepository, never()).save(any());
    verify(applicationRepository, never()).save(any());
  }

  private ProcessDefinition definitionWith(BatchMode batchMode) {
    return new ProcessDefinition(
      BusinessTypeCode.of("ACC_PLAN_CREATE"), DefinitionVersion.initial(),
      StartMode.FORM_DRIVEN, InstanceGranularity.PER_SUB_TASK, batchMode,
      StepCode.of("FORM_DETAIL_INGESTION"),
      List.of(StepDefinition.business(StepCode.of("FORM_DETAIL_INGESTION"),
        TaskType.SYSTEM_TASK, "handler", List.of()))
    );
  }

  private BusinessForm mockForm(BatchId batchId) {
    BusinessForm form = mock(BusinessForm.class);
    when(form.id()).thenReturn(FORM_ID);
    when(form.batchId()).thenReturn(batchId);
    when(form.businessContext()).thenReturn(context());
    when(form.operatorInfo()).thenReturn(operator());
    when(form.domainEvents()).thenReturn(List.of());
    return form;
  }

  private BusinessBatch newBatch() {
    return BusinessBatch.create(BatchId.of("B-1"), context(), operator());
  }

  private BusinessContext context() {
    return new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      CustomerNo.of("C-001"), "客户",
      ProductNo.of("P-001"), "产品",
      PlanNo.of("PL-001"), "方案",
      OperatingMode.TRUSTEE_ONLY, AccountManager.CJP
    );
  }

  private OperatorInfo operator() {
    return new OperatorInfo(Channel.NETAPP, "U-TEST", "操作人", false);
  }
}
