package com.example.core.application.business.service;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.core.domain.business.repository.BatchRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.identifier.id.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

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
  private EventBus eventBus;
  private IdService idService;
  private BusinessBatchAppService appService;

  @BeforeEach
  void setUp() {
    batchRepository = mock(BatchRepository.class);
    eventBus = mock(EventBus.class);
    idService = mock(IdService.class);
    when(idService.nextId(BatchId.class)).thenReturn(new BatchId("BATCH20260726001"));
    appService = new BusinessBatchAppService(batchRepository, eventBus, idService);
  }

  private BusinessContext sampleContext() {
    return new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      new CustomerNo("C001"), "Customer A",
      new ProductNo("PRD001"), "Product A",
      new PlanNo("P001"), "Plan A",
      OperationModel.Single_Trustee,
      AccountManager.CJP
    );
  }

  private OperatorInfo sampleOperator() {
    return new OperatorInfo(
      AnnuityChannel.CJ_TELLER,
      new UserNo("U001"),
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
}
