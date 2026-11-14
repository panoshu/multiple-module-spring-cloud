package com.example.core.domain.business.aggregate.valueobject;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.types.ApplicationId;
import com.example.file.types.FileId;
import com.example.shared.domain.aggregate.valueobject.ValueObject;
import com.example.shared.exception.DomainException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 可选材料能力状态（spec §6.2，批次 37a）。
 * <p>
 * 计划层材料清单与打包上传状态从 {@code BusinessApplication} 核心聚合迁出，
 * 核心聚合只按需持有本对象引用（可为「未启用」的空状态），不再直接持有
 * {@code List<MaterialItem>}/{@code BusinessFile} 字段。
 * <p>
 * 本对象是不可变值对象，所有变更返回新实例；与迁移前一样属于流程内内存态，
 * 不持久化、重建时按入参装配。
 */
public record ApplicationMaterialState(
  List<MaterialItem> planMaterials,
  BusinessFile packageFile) implements ValueObject {

  public ApplicationMaterialState {
    planMaterials = planMaterials == null ? List.of() : List.copyOf(planMaterials);
  }

  /** 未启用材料能力的空状态。 */
  public static ApplicationMaterialState empty() {
    return new ApplicationMaterialState(List.of(), null);
  }

  /** 按持久化重建入参装配；两者皆空时返回共享的空状态。 */
  public static ApplicationMaterialState of(List<MaterialItem> planMaterials, BusinessFile packageFile) {
    if ((planMaterials == null || planMaterials.isEmpty()) && packageFile == null) {
      return empty();
    }
    return new ApplicationMaterialState(planMaterials, packageFile);
  }

  /** 分配材料并智能合并历史附件。 */
  public ApplicationMaterialState assignMaterials(List<MaterialItem> requiredBlueprints) {
    List<MaterialItem> blueprints = requiredBlueprints == null ? List.of() : requiredBlueprints;
    if (this.planMaterials.isEmpty()) {
      return new ApplicationMaterialState(blueprints, this.packageFile);
    }
    Map<String, Optional<MaterialItem.UploadInfo>> historyUploads = this.planMaterials.stream()
      .collect(Collectors.toMap(
        MaterialItem::materialCode,
        MaterialItem::uploadInfo,
        (existing, replacement) -> existing
      ));
    List<MaterialItem> merged = blueprints.stream().map(blueprint -> {
      Optional<MaterialItem.UploadInfo> oldUpload =
        historyUploads.getOrDefault(blueprint.materialCode(), Optional.empty());
      return oldUpload.isPresent()
        ? new MaterialItem(
          blueprint.materialCode(), blueprint.materialName(), blueprint.level(),
          blueprint.requirement(), blueprint.conditionRule(), oldUpload)
        : blueprint;
    }).toList();
    return new ApplicationMaterialState(merged, this.packageFile);
  }

  /** 逐个上传计划层材料；打包模式下禁止。 */
  public ApplicationMaterialState uploadIndividualPlanMaterial(
    String materialCode, BusinessFile file, ApplicationId applicationId) {
    requireNotPackageMode(applicationId, "上传");
    return new ApplicationMaterialState(
      this.planMaterials.stream()
        .map(m -> m.materialCode().equals(materialCode) ? m.withUpload(file) : m)
        .toList(),
      this.packageFile);
  }

  /** 移除某个材料项下指定的已上传文件；打包模式下禁止。 */
  public ApplicationMaterialState removeIndividualPlanMaterial(
    String materialCode, FileId fileId, ApplicationId applicationId) {
    requireNotPackageMode(applicationId, "移除");
    return new ApplicationMaterialState(
      this.planMaterials.stream()
        .map(m -> m.materialCode().equals(materialCode) ? m.removeUpload(fileId) : m)
        .toList(),
      this.packageFile);
  }

  /** 打包上传：记录压缩包并清空此前逐个上传的附件记录。 */
  public ApplicationMaterialState uploadPackage(BusinessFile zipFile) {
    return new ApplicationMaterialState(
      this.planMaterials.stream()
        .map(m -> new MaterialItem(
          m.materialCode(), m.materialName(), m.level(),
          m.requirement(), m.conditionRule(), Optional.empty()))
        .toList(),
      zipFile);
  }

  /** 计划层材料是否满足要求；打包模式直接放行。 */
  public boolean isPlanMaterialSatisfied(MaterialConditionContext context) {
    if (this.packageFile != null) {
      return true;
    }
    return this.planMaterials.stream().allMatch(m -> m.isSatisfied(context));
  }

  public boolean isPackageUploadMode() {
    return this.packageFile != null;
  }

  private void requireNotPackageMode(ApplicationId applicationId, String action) {
    if (this.packageFile != null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_OPERATION)
        .withLogDetail("Application: %s 已使用打包上传模式(%s)，不可逐个%s计划层材料"
          .formatted(applicationId, this.packageFile.fileName(), action));
    }
  }
}
