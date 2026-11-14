package com.example.core.application.business.service;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.types.Channel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.types.OperatingMode;
import com.example.core.domain.business.aggregate.valueobject.enums.status.ApplicationStatus;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.business.repository.BatchRepository;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.contract.IdService;
import com.example.core.types.BatchId;
import com.example.core.types.FormId;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.core.types.CustomerNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * BusinessBatchAppService 单元测试
 *
 * @author panoshu
 */
class BusinessBatchAppServiceTest {

  private BatchRepository batchRepository;
  private ApplicationRepository applicationRepository;
  private IdService idService;
  private BusinessBatchAppService appService;

  @BeforeEach
  void setUp() {
    batchRepository = mock(BatchRepository.class);
    applicationRepository = mock(ApplicationRepository.class);
    idService = mock(IdService.class);
    when(idService.nextId(BatchId.class)).thenReturn(new BatchId("BATCH20260726001"));
    appService = new BusinessBatchAppService(batchRepository, applicationRepository, idService);
  }

  private BusinessContext sampleContext() {
    return new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      new CustomerNo("C001"), "Customer A",
      new ProductNo("PRD001"), "Product A",
      new PlanNo("P001"), "Plan A",
      OperatingMode.TRUSTEE_ONLY,
      AccountManager.CJP
    );
  }

  private OperatorInfo sampleOperator() {
    return new OperatorInfo(
      Channel.TELLER,
      "U001",
      "alice",
      false
    );
  }

  @Test
  void should_create_batch_with_context_and_operator() {
    BusinessContext context = sampleContext();
    OperatorInfo operator = sampleOperator();

    BusinessBatch batch = appService.createBatch(context, operator);

    ArgumentCaptor<BusinessBatch> captor = ArgumentCaptor.forClass(BusinessBatch.class);
    verify(batchRepository).save(captor.capture());
    BusinessBatch saved = captor.getValue();
    assertThat(saved.id()).isNotNull();
    assertThat(saved.id().value()).isEqualTo("BATCH20260726001");
    assertThat(saved.businessContext()).isEqualTo(context);
    assertThat(saved.operatorInfo()).isEqualTo(operator);
    assertThat(saved.status()).isEqualTo(BatchStatus.CREATED);
  }

  @Test
  void should_find_active_batch_by_plan_and_business_type() {
    BusinessBatch batch = mock(BusinessBatch.class);
    PlanNo planNo = new PlanNo("P001");
    BusinessType businessType = BusinessType.ACC_PLAN_CREATE;
    when(batchRepository.findActive(eq(planNo), eq(businessType)))
      .thenReturn(Optional.of(batch));

    Optional<BusinessBatch> result = appService.findActive(planNo, businessType);

    assertThat(result).isPresent();
    verify(batchRepository).findActive(planNo, businessType);
  }

  @Test
  void should_load_batch_or_throw() {
    BatchId batchId = new BatchId("BATCH20260726001");
    BusinessBatch batch = mock(BusinessBatch.class);
    when(batchRepository.loadOrThrow(batchId)).thenReturn(batch);

    BusinessBatch result = appService.loadOrThrow(batchId);

    assertThat(result).isSameAs(batch);
    verify(batchRepository).loadOrThrow(batchId);
  }

  @Test
  void should_cancel_batch_and_publish_event() {
    BatchId batchId = new BatchId("BATCH20260726001");
    BusinessBatch batch = mock(BusinessBatch.class);
    when(batchRepository.loadOrThrow(batchId)).thenReturn(batch);
    when(batch.domainEvents()).thenReturn(java.util.List.of());

    appService.cancel(batchId, "用户主动取消");

    verify(batch).cancel("用户主动取消");
    verify(batchRepository).save(batch);
  }

  @Test
  void should_find_batch_by_form_id() {
    FormId formId = new FormId("FORM001");
    BusinessBatch batch = mock(BusinessBatch.class);
    when(batchRepository.findByFormId(formId)).thenReturn(Optional.of(batch));

    Optional<BusinessBatch> result = appService.findByFormId(formId);

    assertThat(result).isPresent();
    verify(batchRepository).findByFormId(formId);
  }

  @Test
  @DisplayName("对账按批次下申请真实终态修正计数与批次状态")
  void should_reconcile_statistics_from_applications() {
    BatchId batchId = new BatchId("BATCH-DRIFT");
    BusinessBatch batch = driftedBatch(batchId, BatchStatus.PROCESSING, 3, 1, 0);
    List<BusinessApplication> applications = List.of(
      appWithStatus(ApplicationStatus.COMPLETED),
      appWithStatus(ApplicationStatus.COMPLETED),
      appWithStatus(ApplicationStatus.FAILED));
    when(batchRepository.loadOrThrow(batchId)).thenReturn(batch);
    when(applicationRepository.findByBatchId(batchId)).thenReturn(applications);

    appService.reconcileStatistics(batchId, sampleOperator());

    ArgumentCaptor<BusinessBatch> captor = ArgumentCaptor.forClass(BusinessBatch.class);
    verify(batchRepository).save(captor.capture());
    BusinessBatch saved = captor.getValue();
    assertThat(saved.totalApplicationCount()).isEqualTo(3);
    assertThat(saved.successCount()).isEqualTo(2);
    assertThat(saved.failedCount()).isEqualTo(1);
    assertThat(saved.status()).isEqualTo(BatchStatus.PARTIAL_FAILED);
  }

  @Test
  @DisplayName("重复对账不重复计数、不重复发状态事件")
  void should_not_double_count_on_repeated_reconcile() {
    BatchId batchId = new BatchId("BATCH-DRIFT");
    BusinessBatch batch = driftedBatch(batchId, BatchStatus.PROCESSING, 3, 1, 0);
    List<BusinessApplication> applications = List.of(
      appWithStatus(ApplicationStatus.COMPLETED),
      appWithStatus(ApplicationStatus.COMPLETED),
      appWithStatus(ApplicationStatus.FAILED));
    when(batchRepository.loadOrThrow(batchId)).thenReturn(batch);
    when(applicationRepository.findByBatchId(batchId)).thenReturn(applications);

    appService.reconcileStatistics(batchId, sampleOperator());
    long versionAfterFirst = batch.version().value();
    int eventsAfterFirst = batch.domainEvents().size();

    appService.reconcileStatistics(batchId, sampleOperator());

    assertThat(batch.version().value()).isEqualTo(versionAfterFirst);
    assertThat(batch.domainEvents()).hasSize(eventsAfterFirst);
    verify(batchRepository, times(1)).save(batch);
  }

  @Test
  @DisplayName("存在在途申请时批次保持处理中")
  void should_keep_processing_when_applications_in_flight() {
    BatchId batchId = new BatchId("BATCH-DRIFT");
    BusinessBatch batch = driftedBatch(batchId, BatchStatus.PROCESSING, 3, 0, 0);
    List<BusinessApplication> applications = List.of(
      appWithStatus(ApplicationStatus.COMPLETED),
      appWithStatus(ApplicationStatus.RUNNING),
      appWithStatus(ApplicationStatus.WAITING_EXTERNAL));
    when(batchRepository.loadOrThrow(batchId)).thenReturn(batch);
    when(applicationRepository.findByBatchId(batchId)).thenReturn(applications);

    appService.reconcileStatistics(batchId, sampleOperator());

    assertThat(batch.totalApplicationCount()).isEqualTo(3);
    assertThat(batch.successCount()).isEqualTo(1);
    assertThat(batch.status()).isEqualTo(BatchStatus.PROCESSING);
  }

  @Test
  @DisplayName("恢复任务只对活跃批次对账")
  void should_reconcile_only_active_batches() {
    BatchId activeId = new BatchId("BATCH-ACTIVE");
    BusinessBatch active = driftedBatch(activeId, BatchStatus.PROCESSING, 2, 0, 0);
    BusinessBatch completed = driftedBatch(new BatchId("BATCH-DONE"),
      BatchStatus.COMPLETED, 2, 2, 0);
    when(batchRepository.findByStatuses(any())).thenReturn(List.of(active, completed));
    when(batchRepository.loadOrThrow(activeId)).thenReturn(active);
    List<BusinessApplication> activeApplications =
      List.of(appWithStatus(ApplicationStatus.RUNNING));
    when(applicationRepository.findByBatchId(activeId)).thenReturn(activeApplications);

    assertThat(appService.reconcileAllActive()).isEqualTo(1);
    verify(batchRepository, times(1)).loadOrThrow(activeId);
    verify(batchRepository).findByStatuses(List.of(BatchStatus.CREATED, BatchStatus.PROCESSING));
    verify(batchRepository, never()).loadAll();
  }

  @Test
  @DisplayName("单条批次对账失败被隔离，不阻断其余批次")
  void should_isolate_single_batch_failure() {
    BatchId first = new BatchId("BATCH-FAIL");
    BatchId second = new BatchId("BATCH-OK");
    BusinessBatch firstBatch = driftedBatch(first, BatchStatus.PROCESSING, 1, 0, 0);
    BusinessBatch secondBatch = driftedBatch(second, BatchStatus.PROCESSING, 1, 0, 0);
    when(batchRepository.findByStatuses(any())).thenReturn(List.of(firstBatch, secondBatch));
    when(batchRepository.loadOrThrow(first)).thenReturn(firstBatch);
    when(batchRepository.loadOrThrow(second)).thenReturn(secondBatch);
    when(applicationRepository.findByBatchId(first)).thenThrow(new IllegalStateException("db down"));
    List<BusinessApplication> secondApplications =
      List.of(appWithStatus(ApplicationStatus.COMPLETED));
    when(applicationRepository.findByBatchId(second)).thenReturn(secondApplications);

    assertThat(appService.reconcileAllActive()).isEqualTo(1);
    verify(batchRepository).save(secondBatch);
  }

  private BusinessApplication appWithStatus(ApplicationStatus status) {
    BusinessApplication app = mock(BusinessApplication.class);
    when(app.status()).thenReturn(status);
    return app;
  }

  private BusinessBatch driftedBatch(BatchId id, BatchStatus status,
                                     int total, int success, int failed) {
    LocalDateTime now = LocalDateTime.now();
    return BusinessBatch.reconstitute(id, "U-C", "U-U", now.minusDays(1), now,
      Version.initial(), sampleContext(), sampleOperator(), status,
      total, success, failed, List.of());
  }
}
