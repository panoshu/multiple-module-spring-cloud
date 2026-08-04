package com.example.core.domain.business.aggregate.valueobject;

import com.example.core.domain.business.aggregate.valueobject.business.BusinessLevel;
import com.example.core.domain.business.aggregate.valueobject.enums.material.RequirementType;
import com.example.shared.identifier.id.FileId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 业务材料
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 11:28
 */
public record MaterialItem(
  String materialCode,         // 材料编码（业务唯一标识）
  String materialName,
  BusinessLevel level,         // 计划层 / 明细层
  RequirementType requirement, // REQUIRED / OPTIONAL / CONDITIONAL
  String conditionRule,        // 条件必传的规则标识 (如 "age>60")
  Optional<UploadInfo> uploadInfo
) {

  // 判断材料是否满足要求（引入上下文评估条件）
  public boolean isSatisfied(MaterialConditionContext context) {
    return switch (this.requirement) {
      case OPTIONAL -> true; // 非必传，永远满足
      case REQUIRED -> hasFiles(); // 必传，必须有文件
      case CONDITIONAL -> {
        // 条件必传：如果条件命中，则视为必传；否则视为非必传
        boolean conditionHit = context.evaluate(this.conditionRule);
        yield !conditionHit || hasFiles();
      }
    };
  }

  /**
   * 追加文件（每次都返回全新对象，保持绝对不可变）
   */
  public MaterialItem withUpload(BusinessFile file) {
    var currentFiles = uploadInfo.map(UploadInfo::files).orElse(List.of());
    var newFiles = new ArrayList<>(currentFiles);
    newFiles.add(file);

    return new MaterialItem(
      this.materialCode, this.materialName, this.level, this.requirement, this.conditionRule,
      Optional.of(new UploadInfo(LocalDateTime.now(), List.copyOf(newFiles)))
    );
  }

  // 移除文件（保持不可变）
  public MaterialItem removeUpload(FileId fileId) {
    if (uploadInfo.isEmpty()) {
      return this;
    }

    var remaining = uploadInfo.get().files().stream()
      .filter(f -> !f.fileId().equals(fileId))
      .toList();

    return new MaterialItem(
      this.materialCode, this.materialName, this.level, this.requirement, this.conditionRule,
      remaining.isEmpty() ? Optional.empty() : Optional.of(new UploadInfo(LocalDateTime.now(), remaining))
    );
  }

  // 打包上传模式下的快速满足（直接注入压缩包信息，绕过明细校验）
  public MaterialItem markAsPackageSatisfied(BusinessFile zipFile) {
    // 打包模式下，直接将压缩包作为文件附加，且时间取当前
    return new MaterialItem(
      this.materialCode, this.materialName, this.level, this.requirement, this.conditionRule,
      Optional.of(new UploadInfo(LocalDateTime.now(), List.of(zipFile)))
    );
  }

  private boolean hasFiles() {
    return uploadInfo.isPresent() && !uploadInfo.get().files().isEmpty();
  }

  public record UploadInfo(LocalDateTime uploadedAt, List<BusinessFile> files) {
  }
}
