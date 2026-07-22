package com.example.annuity.domain.service;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.shared.domain.annotation.DomainService;

import java.util.Optional;

/**
 * 年金缴费金额校验规则
 * <p>
 * 纯领域规则,无状态、无框架依赖。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@DomainService
public class AnnuityContributionRule {

  private static final long MIN_INITIAL_CONTRIBUTION_FOR_NEW = 10000L;

  /**
   * 校验缴费金额
   *
   * @param ext 年金扩展字段
   * @return 错误消息(为空表示校验通过)
   */
  public Optional<String> validate(AnnuityApplicationExtension ext) {
    if (ext.initialContribution() == null || ext.initialContribution() < 0) {
      return Optional.of("缴费金额不能为负");
    }
    if (AnnuityApplicationExtension.PLAN_TYPE_NEW.equals(ext.planType())
        && ext.initialContribution() < MIN_INITIAL_CONTRIBUTION_FOR_NEW) {
      return Optional.of("新建计划初始缴费不少于 100 元");
    }
    return Optional.empty();
  }
}
