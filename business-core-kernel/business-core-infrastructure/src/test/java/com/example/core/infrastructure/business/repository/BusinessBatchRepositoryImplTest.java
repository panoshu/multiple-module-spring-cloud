package com.example.core.infrastructure.business.repository;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.core.infrastructure.business.converter.BusinessBatchConverter;
import com.example.core.infrastructure.business.entity.BusinessBatchDO;
import com.example.core.infrastructure.business.mapper.BusinessBatchMapper;
import com.example.shared.domain.event.EventBus;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 批次仓储按状态索引查询契约（批次 46，P1-1）。 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BusinessBatchRepositoryImpl 按状态查询")
class BusinessBatchRepositoryImplTest {

  @Mock
  private BusinessBatchMapper mapper;
  @Mock
  private BusinessBatchConverter converter;
  @Mock
  private EventBus eventBus;

  private BusinessBatchRepositoryImpl repository;

  @BeforeEach
  void setUp() {
    repository = new BusinessBatchRepositoryImpl(eventBus, mapper, converter);
  }

  @Test
  @DisplayName("空状态集合直接返回空列表且不查库")
  void findByStatuses_emptyShortCircuits() {
    assertThat(repository.findByStatuses(null)).isEmpty();
    assertThat(repository.findByStatuses(List.of())).isEmpty();
    verifyNoInteractions(mapper);
  }

  @Test
  @DisplayName("按状态条件查询并转换，不走 selectAll 全表扫描")
  void findByStatuses_queriesByStatusColumn() {
    BusinessBatchDO aDo = new BusinessBatchDO();
    BusinessBatch batch = mock(BusinessBatch.class);
    when(mapper.selectListByQuery(any(QueryWrapper.class))).thenReturn(List.of(aDo));
    when(converter.toDomain(aDo)).thenReturn(batch);

    List<BusinessBatch> result = repository.findByStatuses(
      List.of(BatchStatus.CREATED, BatchStatus.PROCESSING));

    assertThat(result).containsExactly(batch);
    verify(mapper).selectListByQuery(any(QueryWrapper.class));
    verify(mapper, never()).selectAll();
  }
}
