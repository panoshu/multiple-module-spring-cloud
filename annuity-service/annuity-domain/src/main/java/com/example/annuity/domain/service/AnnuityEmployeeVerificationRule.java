package com.example.annuity.domain.service;

import com.example.annuity.domain.aggregate.entity.AnnuityEmployeeDetail;
import com.example.core.domain.engine.annotation.DomainService;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 年金员工明细核查规则
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@DomainService
public class AnnuityEmployeeVerificationRule {

  private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{17}[\\dXx]$");
  private static final int MIN_AGE = 18;
  private static final int MAX_AGE = 70;

  /**
   * 核查员工明细
   *
   * @param detail 员工明细
   * @return 错误消息(为空表示核查通过)
   */
  public Optional<String> verify(AnnuityEmployeeDetail detail) {
    if (!ID_CARD_PATTERN.matcher(detail.idCardNo()).matches()) {
      return Optional.of("身份证格式错误: " + detail.idCardNo());
    }
    if (detail.age() == null || detail.age() < MIN_AGE || detail.age() > MAX_AGE) {
      return Optional.of("年龄不在合法区间[" + MIN_AGE + "," + MAX_AGE + "]: " + detail.age());
    }
    if (detail.monthlySalary() == null || detail.monthlySalary() <= 0) {
      return Optional.of("月薪必须为正数: " + detail.monthlySalary());
    }
    if (detail.monthlyContribution() == null || detail.monthlyContribution() <= 0) {
      return Optional.of("月缴费必须为正数: " + detail.monthlyContribution());
    }
    return Optional.empty();
  }
}
