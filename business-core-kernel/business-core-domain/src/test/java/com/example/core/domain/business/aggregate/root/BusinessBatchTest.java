package com.example.core.domain.business.aggregate.root;

import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.core.types.BatchId;
import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BusinessBatch 批次生命周期测试")
class BusinessBatchTest {

  @Test
  @DisplayName("零申请批次保持 CREATED")
  void newBatch_hasNoApplicationsAndStaysCreated() {
    BusinessBatch batch = newBatch();

    assertThat(batch.status()).isEqualTo(BatchStatus.CREATED);
    assertThat(batch.totalApplicationCount()).isZero();
    assertThat(batch.successCount()).isZero();
    assertThat(batch.failedCount()).isZero();
  }

  @Test
  @DisplayName("首个申请推进处理中，后续申请不重复发布状态事件")
  void receiveApplications_updatesCountStatusAndVersion() {
    BusinessBatch batch = newBatch();

    batch.receiveApplications(1, operator());
    assertThat(batch.status()).isEqualTo(BatchStatus.PROCESSING);
    assertThat(batch.totalApplicationCount()).isEqualTo(1);
    assertThat(batch.updatedBy()).isEqualTo("U-TEST");
    assertThat(batch.version().value()).isEqualTo(1L);
    assertThat(batch.domainEvents()).hasSize(1);

    batch.receiveApplications(1, operator());
    assertThat(batch.totalApplicationCount()).isEqualTo(2);
    assertThat(batch.version().value()).isEqualTo(2L);
    assertThat(batch.domainEvents()).hasSize(1);
  }

  @Test
  @DisplayName("一次接收多个申请只执行一次更新")
  void receiveApplications_acceptsMultipleApplications() {
    BusinessBatch batch = newBatch();

    batch.receiveApplications(2, operator());

    assertThat(batch.totalApplicationCount()).isEqualTo(2);
    assertThat(batch.status()).isEqualTo(BatchStatus.PROCESSING);
    assertThat(batch.version().value()).isEqualTo(1L);
  }

  @Test
  @DisplayName("接收申请数量必须大于零")
  void receiveApplications_rejectsNonPositiveCount() {
    BusinessBatch batch = newBatch();

    assertThatThrownBy(() -> batch.receiveApplications(0, operator()))
      .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> batch.receiveApplications(-1, operator()))
      .isInstanceOf(DomainException.class);
  }

  @ParameterizedTest
  @EnumSource(value = BatchStatus.class, names = {"COMPLETED", "PARTIAL_FAILED", "FAILED", "CANCELLED"})
  @DisplayName("终态批次禁止接收申请")
  void receiveApplications_rejectsTerminalBatch(BatchStatus status) {
    BusinessBatch batch = batchWith(status, 2,
      status == BatchStatus.COMPLETED ? 2 : status == BatchStatus.FAILED ? 0 : 1,
      status == BatchStatus.FAILED ? 2 : status == BatchStatus.PARTIAL_FAILED ? 1 : 0);

    assertThatThrownBy(() -> batch.receiveApplications(1, operator()))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("部分申请到达终态时批次保持处理中")
  void registerSuccess_keepsProcessingWhenApplicationsRemain() {
    BusinessBatch batch = processingBatch(2);

    batch.registerSuccess(operator());

    assertThat(batch.status()).isEqualTo(BatchStatus.PROCESSING);
    assertThat(batch.successCount()).isEqualTo(1);
    assertThat(batch.totalApplicationCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("全部申请成功时批次完成")
  void registerSuccess_completesBatchWhenAllSucceeded() {
    BusinessBatch batch = processingBatch(2);

    batch.registerSuccess(operator());
    batch.registerSuccess(operator());

    assertThat(batch.status()).isEqualTo(BatchStatus.COMPLETED);
    assertThat(batch.successCount()).isEqualTo(2);
    assertThat(batch.failedCount()).isZero();
  }

  @Test
  @DisplayName("全部申请失败时批次失败")
  void registerFailure_failsBatchWhenAllFailed() {
    BusinessBatch batch = processingBatch(2);

    batch.registerFailure(operator());
    batch.registerFailure(operator());

    assertThat(batch.status()).isEqualTo(BatchStatus.FAILED);
    assertThat(batch.failedCount()).isEqualTo(2);
  }

  @Test
  @DisplayName("成功与失败并存时批次部分失败")
  void registerResults_marksPartialFailedForMixedResults() {
    BusinessBatch batch = processingBatch(2);

    batch.registerSuccess(operator());
    batch.registerFailure(operator());

    assertThat(batch.status()).isEqualTo(BatchStatus.PARTIAL_FAILED);
    assertThat(batch.successCount()).isEqualTo(1);
    assertThat(batch.failedCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("没有待处理申请时禁止登记结果")
  void registerResult_rejectsWhenNoPendingApplication() {
    BusinessBatch created = newBatch();
    assertThatThrownBy(() -> created.registerSuccess(operator()))
      .isInstanceOf(DomainException.class);

    BusinessBatch completed = processingBatch(1);
    completed.registerSuccess(operator());
    assertThatThrownBy(() -> completed.registerFailure(operator()))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("重建时拒绝非法计数")
  void reconstitute_rejectsInvalidCounts() {
    assertThatThrownBy(() -> batchWith(BatchStatus.PROCESSING, 1, 1, 1))
      .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> batchWith(BatchStatus.CREATED, 1, 0, 0))
      .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> batchWith(BatchStatus.COMPLETED, 0, 0, 0))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("合法终态可以从持久化状态重建")
  void reconstitute_acceptsValidTerminalState() {
    assertThatCode(() -> batchWith(BatchStatus.COMPLETED, 2, 2, 0))
      .doesNotThrowAnyException();
    assertThatCode(() -> batchWith(BatchStatus.PARTIAL_FAILED, 2, 1, 1))
      .doesNotThrowAnyException();
    assertThatCode(() -> batchWith(BatchStatus.FAILED, 2, 0, 2))
      .doesNotThrowAnyException();
  }

  private BusinessBatch processingBatch(int applicationCount) {
    BusinessBatch batch = newBatch();
    batch.receiveApplications(applicationCount, operator());
    return batch;
  }

  private BusinessBatch newBatch() {
    return BusinessBatch.create(BatchId.of("B-1"), context(), operator());
  }

  private BusinessBatch batchWith(BatchStatus status, int total, int success, int failed) {
    if (status == BatchStatus.CANCELLED) {
      total = 0;
      success = 0;
      failed = 0;
    }
    LocalDateTime now = LocalDateTime.now();
    return BusinessBatch.reconstitute(
      BatchId.of("B-R"), "U-C", "U-U", now.minusDays(1), now, Version.initial(),
      context(), operator(), status, total, success, failed, List.of()
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
