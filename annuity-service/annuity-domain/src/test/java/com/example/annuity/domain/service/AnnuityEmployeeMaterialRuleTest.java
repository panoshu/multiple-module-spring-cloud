package com.example.annuity.domain.service;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.domain.aggregate.valueobject.AnnuityEmployeeMaterial;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.ProductNo;
import com.example.shared.identifier.id.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnnuityEmployeeMaterialRule 材料计算规则")
class AnnuityEmployeeMaterialRuleTest {

  private final AnnuityEmployeeMaterialRule rule = new AnnuityEmployeeMaterialRule();

  @Test
  @DisplayName("普通员工生成 3 项基础材料")
  void calculate_normalEmployee() {
    AnnuityEmployeeDetail detail = createDetail();
    BusinessMetaContext context = createContext(Map.of("hasForeignInvestment", false));
    List<AnnuityEmployeeMaterial> materials = rule.calculate(detail, context);
    assertThat(materials).hasSize(3);
    assertThat(materials).allMatch(AnnuityEmployeeMaterial::required);
  }

  @Test
  @DisplayName("外资员工额外生成外资资产申报表")
  void calculate_foreignInvestmentEmployee() {
    AnnuityEmployeeDetail detail = createDetail();
    BusinessMetaContext context = createContext(Map.of("hasForeignInvestment", true));
    List<AnnuityEmployeeMaterial> materials = rule.calculate(detail, context);
    assertThat(materials).hasSize(4);
    assertThat(materials).anyMatch(m -> "FOREIGN_ASSET_DECL".equals(m.materialCode()));
  }

  private AnnuityEmployeeDetail createDetail() {
    return new AnnuityEmployeeDetail(
      AnnuityEmployeeDetailId.of("D-001"),
      AnnuityEmployeeBatchId.of("B-001"),
      "张三", "110101199001011234", 35, 10000L, 500L, UserNo.of("U-TEST")
    );
  }

  private BusinessMetaContext createContext(Map<String, Object> extensionFacts) {
    return new BusinessMetaContext(
      CustomerNo.of("C-001"), ProductNo.of("P-001"),
      OperationModel.Single_Trustee, PlanNo.of("PL-001"),
      BusinessType.ACC_PLAN_CREATE, AccountManager.CJP, extensionFacts
    );
  }
}
