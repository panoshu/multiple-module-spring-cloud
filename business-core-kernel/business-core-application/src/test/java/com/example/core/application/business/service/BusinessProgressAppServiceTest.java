package com.example.core.application.business.service;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.repository.BatchRepository;
import com.example.shared.identifier.id.BatchId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * BusinessProgressAppService 单元测试
 *
 * @author panoshu
 */
class BusinessProgressAppServiceTest {

  private BatchRepository batchRepository;
  private BusinessProgressAppService appService;

  @BeforeEach
  void setUp() {
    batchRepository = mock(BatchRepository.class);
    appService = new BusinessProgressAppService(batchRepository);
  }

  @Test
  void should_get_batch_progress() {
    BatchId batchId = new BatchId("BATCH001");
    BusinessBatch batch = mock(BusinessBatch.class);
    when(batchRepository.loadOrThrow(batchId)).thenReturn(batch);

    BusinessBatch result = appService.getBatchProgress(batchId);

    assertThat(result).isSameAs(batch);
    verify(batchRepository).loadOrThrow(batchId);
  }
}
