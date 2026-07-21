package com.example.annuity.domain.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 年金员工材料值对象
 *
 * @param materialCode 材料代码
 * @param materialName 材料名称
 * @param required     是否必传
 * @param uploaded     是否已上传
 * @param description  描述
 * @author annuity-service
 * @since 2026/7/22
 */
public record AnnuityEmployeeMaterial(
    String materialCode,
    String materialName,
    boolean required,
    boolean uploaded,
    String description
) implements ValueObject {

  public AnnuityEmployeeMaterial {
    if (materialCode == null || materialCode.isBlank()) {
      throw new IllegalArgumentException("materialCode cannot be blank");
    }
    if (materialName == null || materialName.isBlank()) {
      throw new IllegalArgumentException("materialName cannot be blank");
    }
  }

  /**
   * 标记材料已上传,返回新实例(不可变)
   */
  public AnnuityEmployeeMaterial markUploaded() {
    return new AnnuityEmployeeMaterial(materialCode, materialName, required, true, description);
  }
}
