package com.example.core.domain.business.aggregate.root;

import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BusinessBatch 公开 accessor 验证")
class BusinessBatchAccessorTest {

  @Test
  @DisplayName("create() 创建初始状态为 CREATED 的批次")
  void create_setsCreatedStatus() {
    BusinessBatch batch = BusinessBatch.create(
      BatchId.of("B-1"), buildContext(), buildOperator()
    );

    assertThat(batch.status()).isEqualTo(BatchStatus.CREATED);
    assertThat(batch.businessContext().businessType()).isEqualTo(BusinessType.ACC_PLAN_CREATE);
    assertThat(batch.operatorInfo().operatorId().value()).isEqualTo("U-TEST");
    assertThat(batch.totalApplicationCount()).isZero();
    assertThat(batch.successCount()).isZero();
    assertThat(batch.failedCount()).isZero();
  }

  @Test
  @DisplayName("reconstitute() 装配所有字段且不触发业务事件")
  void reconstitute_assemblesAllFields() {
    LocalDateTime now = LocalDateTime.now();

    BusinessBatch batch = BusinessBatch.reconstitute(
      BatchId.of("B-R"), UserNo.of("U-C"), UserNo.of("U-U"),
      now.minusDays(1), now, Version.of(2L),
      buildContext(), buildOperator(),
      BatchStatus.PROCESSING,
      10, 7, 3,
      List.of()
    );

    assertThat(batch.id().value()).isEqualTo("B-R");
    assertThat(batch.status()).isEqualTo(BatchStatus.PROCESSING);
    assertThat(batch.totalApplicationCount()).isEqualTo(10);
    assertThat(batch.successCount()).isEqualTo(7);
    assertThat(batch.failedCount()).isEqualTo(3);
    assertThat(batch.businessFormRefs()).isEmpty();
    assertThat(batch.version().value()).isEqualTo(2L);
    assertThat(batch.domainEvents()).isEmpty();
  }

  private BusinessContext buildContext() {
    return new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      CustomerNo.of("C-001"), "客户",
      ProductNo.of("P-001"), "产品",
      PlanNo.of("PL-001"), "方案",
      OperationModel.Single_Trustee, AccountManager.CJP
    );
  }

  private OperatorInfo buildOperator() {
    return new OperatorInfo(
      AnnuityChannel.NETAPP, UserNo.of("U-TEST"), "操作人", false
    );
  }
}
