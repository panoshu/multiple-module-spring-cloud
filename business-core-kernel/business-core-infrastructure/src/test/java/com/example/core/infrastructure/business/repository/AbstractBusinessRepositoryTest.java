package com.example.core.infrastructure.business.repository;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.core.infrastructure.business.converter.BusinessBatchConverter;
import com.example.core.infrastructure.business.entity.BusinessBatchDO;
import com.example.core.infrastructure.business.mapper.BusinessBatchMapper;
import com.example.core.types.BatchId;
import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.SystemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("通用业务仓储乐观锁测试")
class AbstractBusinessRepositoryTest {

  @Mock
  private BusinessBatchMapper mapper;
  @Mock
  private BusinessBatchConverter converter;
  @Mock
  private ApplicationEventPublisher eventPublisher;

  private BusinessBatchRepositoryImpl repository;

  @BeforeEach
  void setUp() {
    repository = new BusinessBatchRepositoryImpl(eventPublisher, mapper, converter);
  }

  @Test
  @DisplayName("领域版本未预先递增时保存后只回写一次")
  void save_syncsVersionWhenDomainDidNotIncrement() {
    BusinessBatch batch = batchWithVersion(3);
    BusinessBatchDO updateDO = doWithVersion(3);
    stubExistingUpdate(batch, updateDO, 3, 1);

    repository.save(batch);

    ArgumentCaptor<BusinessBatchDO> captor = ArgumentCaptor.forClass(BusinessBatchDO.class);
    verify(mapper).update(captor.capture());
    assertThat(captor.getValue().getVersion()).isEqualTo(3);
    assertThat(batch.version().value()).isEqualTo(4L);
  }

  @Test
  @DisplayName("markUpdated 已递增领域版本时不重复回写")
  void save_doesNotSyncVersionTwiceWhenDomainAlreadyIncremented() {
    BusinessBatch batch = batchWithVersion(3);
    batch.receiveApplications(1, operator());
    BusinessBatchDO updateDO = doWithVersion(4);
    stubExistingUpdate(batch, updateDO, 3, 1);

    repository.save(batch);

    ArgumentCaptor<BusinessBatchDO> captor = ArgumentCaptor.forClass(BusinessBatchDO.class);
    verify(mapper).update(captor.capture());
    assertThat(captor.getValue().getVersion()).isEqualTo(3);
    assertThat(batch.version().value()).isEqualTo(4L);
  }

  @Test
  @DisplayName("领域版本与数据库版本不兼容时拒绝更新")
  void save_rejectsUnexpectedVersionDifference() {
    BusinessBatch batch = batchWithVersion(5);
    BusinessBatchDO updateDO = doWithVersion(5);
    when(mapper.selectOneById("B-1")).thenReturn(doWithVersion(3));
    when(converter.toDO(batch)).thenReturn(updateDO);

    assertThatThrownBy(() -> repository.save(batch))
      .isInstanceOf(SystemException.class);
    verify(mapper, never()).update(any());
  }

  @Test
  @DisplayName("数据库更新行数为零时显式失败")
  void save_rejectsZeroUpdatedRows() {
    BusinessBatch batch = batchWithVersion(3);
    BusinessBatchDO updateDO = doWithVersion(3);
    stubExistingUpdate(batch, updateDO, 3, 0);

    assertThatThrownBy(() -> repository.save(batch))
      .isInstanceOf(SystemException.class);
    assertThat(batch.version().value()).isEqualTo(3L);
  }

  private void stubExistingUpdate(BusinessBatch batch, BusinessBatchDO updateDO,
                                  int persistedVersion, int updateResult) {
    BusinessBatchDO existing = doWithVersion(persistedVersion);
    when(mapper.selectOneById("B-1")).thenReturn(existing);
    when(converter.toDO(batch)).thenReturn(updateDO);
    when(mapper.update(any(BusinessBatchDO.class))).thenReturn(updateResult);
  }

  private BusinessBatchDO doWithVersion(int version) {
    BusinessBatchDO aDo = new BusinessBatchDO();
    aDo.setId("B-1");
    aDo.setVersion(version);
    return aDo;
  }

  private BusinessBatch batchWithVersion(long version) {
    LocalDateTime now = LocalDateTime.now();
    return BusinessBatch.reconstitute(
      BatchId.of("B-1"), "U-C", "U-U", now.minusDays(1), now, Version.of(version),
      context(), operator(), BatchStatus.PROCESSING, 1, 0, 0, List.of()
    );
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
