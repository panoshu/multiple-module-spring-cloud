package com.example.annuity.domain.service;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnnuityContributionRule 缴费校验规则")
class AnnuityContributionRuleTest {

  private final AnnuityContributionRule rule = new AnnuityContributionRule();

  @Test
  @DisplayName("负数缴费金额校验失败")
  void validate_negativeContributionFails() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", -100L, false
    );
    assertThat(rule.validate(ext)).isPresent()
        .hasValueSatisfying(msg -> assertThat(msg).contains("不能为负"));
  }

  @Test
  @DisplayName("新建计划缴费不足 100 元校验失败")
  void validate_newPlanBelowThresholdFails() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 5000L, false
    );
    assertThat(rule.validate(ext)).isPresent()
        .hasValueSatisfying(msg -> assertThat(msg).contains("不少于 100 元"));
  }

  @Test
  @DisplayName("修改计划缴费金额无阈值校验")
  void validate_modifyPlanNoThreshold() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_MODIFY, "MODIFY", 100L, false
    );
    assertThat(rule.validate(ext)).isEmpty();
  }

  @Test
  @DisplayName("新建计划缴费达标校验通过")
  void validate_newPlanAtThresholdPasses() {
    AnnuityApplicationExtension ext = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE, "NEW", 10000L, false
    );
    assertThat(rule.validate(ext)).isEmpty();
  }
}
