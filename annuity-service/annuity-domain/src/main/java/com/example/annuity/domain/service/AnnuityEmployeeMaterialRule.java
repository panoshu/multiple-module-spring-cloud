package com.example.annuity.domain.service;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeMaterial;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.annotation.DomainService;

import java.util.ArrayList;
import java.util.List;

/**
 * 年金员工材料计算规则
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@DomainService
public class AnnuityEmployeeMaterialRule {

  private static final String MATERIAL_ID_CARD = "ID_CARD_COPY";
  private static final String MATERIAL_EMPLOYMENT_CERT = "EMPLOYMENT_CERT";
  private static final String MATERIAL_SALARY_PROOF = "SALARY_PROOF";
  private static final String MATERIAL_FOREIGN_DECL = "FOREIGN_ASSET_DECL";

  /**
   * 计算员工材料清单
   *
   * @param detail   员工明细
   * @param context  业务上下文(用于判断外资场景)
   * @return 材料清单
   */
  public List<AnnuityEmployeeMaterial> calculate(AnnuityEmployeeDetail detail, BusinessMetaContext context) {
    List<AnnuityEmployeeMaterial> materials = new ArrayList<>();
    materials.add(new AnnuityEmployeeMaterial(MATERIAL_ID_CARD, "身份证复印件", true, false, null));
    materials.add(new AnnuityEmployeeMaterial(MATERIAL_EMPLOYMENT_CERT, "在职证明", true, false, null));
    materials.add(new AnnuityEmployeeMaterial(MATERIAL_SALARY_PROOF, "收入证明", true, false, null));

    Object hasForeign = context.extensionFacts() == null ? null : context.extensionFacts().get("hasForeignInvestment");
    if (Boolean.TRUE.equals(hasForeign)) {
      materials.add(new AnnuityEmployeeMaterial(MATERIAL_FOREIGN_DECL, "外资资产申报表", true, false, "含外资业务必传"));
    }
    return materials;
  }
}
