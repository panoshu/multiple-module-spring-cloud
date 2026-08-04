package com.example.annuity.domain.aggregate.entity;

import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeDetailStatus;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeMaterial;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AnnuityEmployeeDetail 实体行为")
class AnnuityEmployeeDetailTest {

  private static final UserNo OPERATOR = UserNo.of("U-TEST");

  @Test
  @DisplayName("verify 成功后状态变为 VERIFIED")
  void verify_changesStatusToVerified() {
    AnnuityEmployeeDetail detail = createDetail();
    detail.verify(OPERATOR);
    assertThat(detail.status()).isEqualTo(AnnuityEmployeeDetailStatus.VERIFIED);
    assertThat(detail.verifiedAt()).isNotNull();
  }

  @Test
  @DisplayName("markAnomaly 记录异常原因并改变状态,同时递增版本号")
  void markAnomaly_recordsReasonAndChangesStatus() {
    AnnuityEmployeeDetail detail = createDetail();
    Version initialVersion = detail.version();
    detail.markAnomaly("身份证格式错误", OPERATOR);
    assertThat(detail.status()).isEqualTo(AnnuityEmployeeDetailStatus.ANOMALY);
    assertThat(detail.anomalyReason()).isEqualTo("身份证格式错误");
    assertThat(detail.version()).isEqualTo(initialVersion.next());
    assertThat(detail.updatedBy()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("markAnomaly 在已标记异常时抛出异常")
  void markAnomaly_throwsWhenAlreadyAnomaly() {
    AnnuityEmployeeDetail detail = createDetail();
    detail.markAnomaly("身份证格式错误", OPERATOR);
    assertThatThrownBy(() -> detail.markAnomaly("再次异常", OPERATOR))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("不可重复标记");
  }

  @Test
  @DisplayName("assignMaterials 为明细挂载材料清单,同时递增版本号")
  void assignMaterials_attachesMaterialList() {
    AnnuityEmployeeDetail detail = createDetail();
    detail.verify(OPERATOR);
    Version initialVersion = detail.version();
    List<AnnuityEmployeeMaterial> materials = List.of(
      new AnnuityEmployeeMaterial("ID_CARD", "身份证复印件", true, false, null)
    );
    detail.assignMaterials(materials, OPERATOR);
    assertThat(detail.materials()).hasSize(1);
    assertThat(detail.materialPreparedAt()).isNotNull();
    assertThat(detail.version()).isEqualTo(initialVersion.next());
    assertThat(detail.updatedBy()).isEqualTo(OPERATOR);
  }

  @Test
  @DisplayName("assignMaterials 在未核查状态下抛出异常")
  void assignMaterials_throwsWhenNotVerified() {
    AnnuityEmployeeDetail detail = createDetail();
    assertThatThrownBy(() -> detail.assignMaterials(List.of(), OPERATOR))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("未核查");
  }

  @Test
  @DisplayName("isMaterialSatisfied 在所有必传材料已上传时返回 true")
  void isMaterialSatisfied_returnsTrueWhenAllRequiredUploaded() {
    AnnuityEmployeeDetail detail = createDetail();
    detail.verify(OPERATOR);
    detail.assignMaterials(List.of(
      new AnnuityEmployeeMaterial("ID_CARD", "身份证", true, true, null),
      new AnnuityEmployeeMaterial("SALARY", "收入证明", true, true, null),
      new AnnuityEmployeeMaterial("EXTRA", "可选材料", false, false, null)
    ), OPERATOR);
    assertThat(detail.isMaterialSatisfied()).isTrue();
  }

  @Test
  @DisplayName("isMaterialSatisfied 在必传材料未上传时返回 false")
  void isMaterialSatisfied_returnsFalseWhenRequiredMissing() {
    AnnuityEmployeeDetail detail = createDetail();
    detail.verify(OPERATOR);
    detail.assignMaterials(List.of(
      new AnnuityEmployeeMaterial("ID_CARD", "身份证", true, true, null),
      new AnnuityEmployeeMaterial("SALARY", "收入证明", true, false, null)
    ), OPERATOR);
    assertThat(detail.isMaterialSatisfied()).isFalse();
  }

  private AnnuityEmployeeDetail createDetail() {
    return new AnnuityEmployeeDetail(
      AnnuityEmployeeDetailId.of("D-001"),
      AnnuityEmployeeBatchId.of("B-001"),
      "张三", "110101199001011234", 35, 10000L, 500L,
      OPERATOR
    );
  }
}
