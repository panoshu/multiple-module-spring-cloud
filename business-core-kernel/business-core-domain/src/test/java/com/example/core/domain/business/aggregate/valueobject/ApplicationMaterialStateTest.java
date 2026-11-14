package com.example.core.domain.business.aggregate.valueobject;

import com.example.core.domain.business.aggregate.valueobject.enums.material.RequirementType;
import com.example.core.types.ApplicationId;
import com.example.file.types.FileId;
import com.example.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ApplicationMaterialState 可选材料能力")
class ApplicationMaterialStateTest {

  private static final ApplicationId APP_ID = new ApplicationId("APP-1");

  @Test
  @DisplayName("空状态无材料、非打包模式")
  void empty_hasNoMaterialsAndNoPackage() {
    ApplicationMaterialState state = ApplicationMaterialState.empty();

    assertThat(state.planMaterials()).isEmpty();
    assertThat(state.packageFile()).isNull();
    assertThat(state.isPackageUploadMode()).isFalse();
  }

  @Test
  @DisplayName("首次分配直接采用蓝图清单")
  void assignMaterials_firstAssignmentUsesBlueprints() {
    MaterialItem item = required("M1");

    ApplicationMaterialState state = ApplicationMaterialState.empty()
      .assignMaterials(List.of(item));

    assertThat(state.planMaterials()).containsExactly(item);
  }

  @Test
  @DisplayName("重新分配时合并历史上传记录")
  void assignMaterials_mergesHistoryUploads() {
    BusinessFile uploaded = file("F-1");
    ApplicationMaterialState withUpload = ApplicationMaterialState.empty()
      .assignMaterials(List.of(required("M1")))
      .uploadIndividualPlanMaterial("M1", uploaded, APP_ID);

    ApplicationMaterialState merged = withUpload.assignMaterials(List.of(required("M1")));

    assertThat(merged.planMaterials()).hasSize(1);
    assertThat(merged.planMaterials().get(0).uploadInfo()).isPresent();
    assertThat(merged.planMaterials().get(0).uploadInfo().get().files()).containsExactly(uploaded);
  }

  @Test
  @DisplayName("打包模式下禁止逐个上传/移除")
  void packageMode_rejectsIndividualOperations() {
    ApplicationMaterialState state = ApplicationMaterialState.empty()
      .assignMaterials(List.of(required("M1")))
      .uploadPackage(file("PKG"));

    assertThatThrownBy(() -> state.uploadIndividualPlanMaterial("M1", file("F-1"), APP_ID))
      .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> state.removeIndividualPlanMaterial("M1", new FileId("F-1"), APP_ID))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("打包上传清空已有上传记录并进入打包模式")
  void uploadPackage_clearsUploadsAndEnablesPackageMode() {
    ApplicationMaterialState state = ApplicationMaterialState.empty()
      .assignMaterials(List.of(required("M1")))
      .uploadIndividualPlanMaterial("M1", file("F-1"), APP_ID);

    ApplicationMaterialState packaged = state.uploadPackage(file("PKG"));

    assertThat(packaged.isPackageUploadMode()).isTrue();
    assertThat(packaged.planMaterials().get(0).uploadInfo()).isEmpty();
    assertThat(packaged.isPlanMaterialSatisfied(null)).isTrue();
    assertThat(state.isPackageUploadMode()).isFalse();
  }

  @Test
  @DisplayName("移除指定文件后该材料回到未上传")
  void removeIndividualPlanMaterial_removesOnlyMatchingFile() {
    ApplicationMaterialState state = ApplicationMaterialState.empty()
      .assignMaterials(List.of(required("M1")))
      .uploadIndividualPlanMaterial("M1", file("F-1"), APP_ID);

    ApplicationMaterialState removed =
      state.removeIndividualPlanMaterial("M1", new FileId("F-1"), APP_ID);

    assertThat(removed.planMaterials().get(0).uploadInfo()).isEmpty();
  }

  @Test
  @DisplayName("所有变更返回新实例，原状态不变")
  void stateChanges_areImmutable() {
    ApplicationMaterialState original = ApplicationMaterialState.empty();

    ApplicationMaterialState changed = original.assignMaterials(List.of(required("M1")));

    assertThat(changed).isNotSameAs(original);
    assertThat(original.planMaterials()).isEmpty();
  }

  private MaterialItem required(String code) {
    return new MaterialItem(code, code, null, RequirementType.REQUIRED, null, Optional.empty());
  }

  private BusinessFile file(String id) {
    return new BusinessFile(new FileId(id), id + ".pdf", "pdf", 100L);
  }
}
