package com.example.annuity.domain.service;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.annuity.types.AnnuityEmployeeDetailId;
import com.example.shared.identifier.id.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AnnuityEmployeeVerificationRule 员工核查规则")
class AnnuityEmployeeVerificationRuleTest {

  private final AnnuityEmployeeVerificationRule rule = new AnnuityEmployeeVerificationRule();

  @Test
  @DisplayName("身份证格式错误 - 校验失败")
  void verify_invalidIdCardFormat() {
    AnnuityEmployeeDetail detail = createDetail("123", 35, 10000L, 500L);
    assertThat(rule.verify(detail)).isPresent()
      .hasValueSatisfying(msg -> assertThat(msg).contains("身份证格式错误"));
  }

  @Test
  @DisplayName("年龄小于 18 - 校验失败")
  void verify_ageBelowMinimum() {
    AnnuityEmployeeDetail detail = createDetail("110101199001011234", 17, 10000L, 500L);
    assertThat(rule.verify(detail)).isPresent()
      .hasValueSatisfying(msg -> assertThat(msg).contains("年龄不在合法区间"));
  }

  @Test
  @DisplayName("年龄大于 70 - 校验失败")
  void verify_ageAboveMaximum() {
    AnnuityEmployeeDetail detail = createDetail("110101199001011234", 71, 10000L, 500L);
    assertThat(rule.verify(detail)).isPresent()
      .hasValueSatisfying(msg -> assertThat(msg).contains("年龄不在合法区间"));
  }

  @Test
  @DisplayName("月薪为 0 - 校验失败")
  void verify_zeroSalary() {
    AnnuityEmployeeDetail detail = createDetail("110101199001011234", 35, 0L, 500L);
    assertThat(rule.verify(detail)).isPresent()
      .hasValueSatisfying(msg -> assertThat(msg).contains("月薪必须为正数"));
  }

  @Test
  @DisplayName("月缴费为 0 - 校验失败")
  void verify_zeroContribution() {
    AnnuityEmployeeDetail detail = createDetail("110101199001011234", 35, 10000L, 0L);
    assertThat(rule.verify(detail)).isPresent()
      .hasValueSatisfying(msg -> assertThat(msg).contains("月缴费必须为正数"));
  }

  @Test
  @DisplayName("全部合法 - 校验通过")
  void verify_validDetail() {
    AnnuityEmployeeDetail detail = createDetail("110101199001011234", 35, 10000L, 500L);
    assertThat(rule.verify(detail)).isEmpty();
  }

  private AnnuityEmployeeDetail createDetail(String idCard, int age, long salary, long contribution) {
    return new AnnuityEmployeeDetail(
      AnnuityEmployeeDetailId.of("D-001"),
      AnnuityEmployeeBatchId.of("B-001"),
      "张三", idCard, age, salary, contribution, UserNo.of("U-TEST")
    );
  }
}
