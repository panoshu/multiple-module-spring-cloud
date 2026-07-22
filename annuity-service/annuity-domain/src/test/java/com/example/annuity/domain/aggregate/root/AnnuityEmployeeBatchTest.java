package com.example.annuity.domain.aggregate.root;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeBatchStatus;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeDetailStatus;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AnnuityEmployeeBatch 聚合根行为")
class AnnuityEmployeeBatchTest {

  private static final UserNo OPERATOR = UserNo.of("U-TEST");
  private static final ApplicationId APP_ID = new ApplicationId("APP-001");

  @Test
  @DisplayName("create 工厂方法初始化 PENDING 状态和空明细集合")
  void create_initializesPendingStatusAndEmptyDetails() {
    AnnuityEmployeeBatch batch = AnnuityEmployeeBatch.create(
        AnnuityEmployeeBatchId.of("B-001"), APP_ID, 10, OPERATOR
    );
    assertThat(batch.status()).isEqualTo(AnnuityEmployeeBatchStatus.PENDING);
    assertThat(batch.totalEmployeeCount()).isEqualTo(10);
    assertThat(batch.details()).isEmpty();
    assertThat(batch.processedCount()).isZero();
    assertThat(batch.anomalyCount()).isZero();
  }

  @Test
  @DisplayName("addDetail 添加明细到批次")
  void addDetail_addsToBatch() {
    AnnuityEmployeeBatch batch = createBatch(1);
    batch.addDetail(createDetail("D-001", "张三"));
    assertThat(batch.details()).hasSize(1);
    assertThat(batch.pendingDetails()).hasSize(1);
  }

  @Test
  @DisplayName("markDetailProcessed 递增 processedCount")
  void markDetailProcessed_incrementsProcessedCount() {
    AnnuityEmployeeBatch batch = createBatch(2);
    batch.addDetail(createDetail("D-001", "张三"));
    batch.addDetail(createDetail("D-002", "李四"));
    batch.markDetailProcessed(AnnuityEmployeeDetailId.of("D-001"), OPERATOR);
    assertThat(batch.processedCount()).isEqualTo(1);
    assertThat(batch.isAllProcessed()).isFalse();
  }

  @Test
  @DisplayName("markDetailAnomaly 递增 anomalyCount 并更新版本号")
  void markDetailAnomaly_incrementsAnomalyCountAndVersion() {
    AnnuityEmployeeBatch batch = createBatch(1);
    batch.addDetail(createDetail("D-001", "张三"));
    Version initialVersion = batch.version();
    batch.markDetailAnomaly(AnnuityEmployeeDetailId.of("D-001"), "身份证错误", OPERATOR);
    assertThat(batch.anomalyCount()).isEqualTo(1);
    assertThat(batch.version()).isEqualTo(initialVersion.next());
    assertThat(batch.updatedBy()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("complete 在所有明细处理完后将状态置为 COMPLETED 并更新版本号")
  void complete_changesStatusToCompletedAndIncrementsVersion() {
    AnnuityEmployeeBatch batch = createBatch(1);
    batch.addDetail(createDetail("D-001", "张三"));
    batch.markDetailProcessed(AnnuityEmployeeDetailId.of("D-001"), OPERATOR);
    Version initialVersion = batch.version();
    batch.complete(OPERATOR);
    assertThat(batch.status()).isEqualTo(AnnuityEmployeeBatchStatus.COMPLETED);
    assertThat(batch.version()).isEqualTo(initialVersion.next());
  }

  @Test
  @DisplayName("fail 将状态置为 FAILED 并更新版本号")
  void fail_changesStatusToFailedAndIncrementsVersion() {
    AnnuityEmployeeBatch batch = createBatch(1);
    Version initialVersion = batch.version();
    batch.fail("存在异常明细", OPERATOR);
    assertThat(batch.status()).isEqualTo(AnnuityEmployeeBatchStatus.FAILED);
    assertThat(batch.version()).isEqualTo(initialVersion.next());
    assertThat(batch.updatedBy()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("complete 在尚有未处理明细时抛出异常")
  void complete_throwsWhenNotAllProcessed() {
    AnnuityEmployeeBatch batch = createBatch(1);
    batch.addDetail(createDetail("D-001", "张三"));
    assertThatThrownBy(() -> batch.complete(OPERATOR))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("未全部处理");
  }

  @Test
  @DisplayName("verifiedDetails 仅返回 VERIFIED 状态的明细")
  void verifiedDetails_returnsOnlyVerified() {
    AnnuityEmployeeBatch batch = createBatch(2);
    batch.addDetail(createDetail("D-001", "张三"));
    batch.addDetail(createDetail("D-002", "李四"));
    batch.markDetailAnomaly(AnnuityEmployeeDetailId.of("D-002"), "异常", OPERATOR);
    batch.markDetailProcessed(AnnuityEmployeeDetailId.of("D-001"), OPERATOR);
    // markDetailProcessed 内部调用 detail.verify(operator),将明细状态变更为 VERIFIED
    assertThat(batch.verifiedDetails()).hasSize(1);
    assertThat(batch.verifiedDetails().get(0).id()).isEqualTo(AnnuityEmployeeDetailId.of("D-001"));
    assertThat(batch.pendingDetails()).isEmpty();
    assertThat(batch.anomalyCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("markDetailProcessed 在明细已处理时抛出异常")
  void markDetailProcessed_throwsWhenAlreadyProcessed() {
    AnnuityEmployeeBatch batch = createBatch(1);
    batch.addDetail(createDetail("D-001", "张三"));
    batch.markDetailProcessed(AnnuityEmployeeDetailId.of("D-001"), OPERATOR);
    assertThatThrownBy(() -> batch.markDetailProcessed(AnnuityEmployeeDetailId.of("D-001"), OPERATOR))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("不可重复处理");
  }

  @Test
  @DisplayName("markDetailAnomaly 在明细已标记异常时抛出异常")
  void markDetailAnomaly_throwsWhenAlreadyAnomaly() {
    AnnuityEmployeeBatch batch = createBatch(1);
    batch.addDetail(createDetail("D-001", "张三"));
    batch.markDetailAnomaly(AnnuityEmployeeDetailId.of("D-001"), "异常", OPERATOR);
    assertThatThrownBy(() -> batch.markDetailAnomaly(AnnuityEmployeeDetailId.of("D-001"), "再次异常", OPERATOR))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("不可重复标记");
  }

  private AnnuityEmployeeBatch createBatch(int total) {
    return AnnuityEmployeeBatch.create(
        AnnuityEmployeeBatchId.of("B-001"), APP_ID, total, OPERATOR
    );
  }

  private AnnuityEmployeeDetail createDetail(String id, String name) {
    return new AnnuityEmployeeDetail(
        AnnuityEmployeeDetailId.of(id),
        AnnuityEmployeeBatchId.of("B-001"),
        name, "110101199001011234", 35, 10000L, 500L, OPERATOR
    );
  }
}
