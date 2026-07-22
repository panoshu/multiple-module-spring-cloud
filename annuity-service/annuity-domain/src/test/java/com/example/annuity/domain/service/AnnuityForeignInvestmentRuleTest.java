package com.example.annuity.domain.service;

import com.example.annuity.domain.aggregate.valueobject.CustomerProfile;
import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import com.example.shared.primitives.identity.CustomerNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnnuityForeignInvestmentRule 外资准入规则")
class AnnuityForeignInvestmentRuleTest {

  private final AnnuityForeignInvestmentRule rule = new AnnuityForeignInvestmentRule();

  @Test
  @DisplayName("声明外资但画像无外资企业 - 校验失败")
  void validate_declaredButNoForeignCompany() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 20000L, true
    );
    CustomerProfile profile = new CustomerProfile(
        CustomerNo.of("C-001"), "LOW", List.of("CJ-PENSION")
    );
    assertThat(rule.validate(ext, profile)).isPresent()
        .hasValueSatisfying(msg -> assertThat(msg).contains("未关联外资企业"));
  }

  @Test
  @DisplayName("画像含外资但未声明 - 校验失败")
  void validate_companyForeignButNotDeclared() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 20000L, false
    );
    CustomerProfile profile = new CustomerProfile(
        CustomerNo.of("C-001"), "MEDIUM", List.of("FOREIGN-CO")
    );
    assertThat(rule.validate(ext, profile)).isPresent()
        .hasValueSatisfying(msg -> assertThat(msg).contains("需人工复核"));
  }

  @Test
  @DisplayName("声明与画像一致 - 校验通过")
  void validate_consistentDeclaration() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 20000L, true
    );
    CustomerProfile profile = new CustomerProfile(
        CustomerNo.of("C-001"), "MEDIUM", List.of("FOREIGN-CO")
    );
    assertThat(rule.validate(ext, profile)).isEmpty();
  }

  @Test
  @DisplayName("均无外资 - 校验通过")
  void validate_noForeignInvestment() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 20000L, false
    );
    CustomerProfile profile = new CustomerProfile(
        CustomerNo.of("C-001"), "LOW", List.of("CJ-PENSION")
    );
    assertThat(rule.validate(ext, profile)).isEmpty();
  }
}
