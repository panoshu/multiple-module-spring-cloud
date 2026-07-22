package com.example.annuity.domain.service;

import com.example.annuity.domain.aggregate.valueobject.CustomerProfile;
import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.shared.domain.annotation.DomainService;

import java.util.Optional;

/**
 * 年金外资准入规则
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@DomainService
public class AnnuityForeignInvestmentRule {

  /**
   * 校验外资准入
   *
   * @param ext     年金扩展字段
   * @param profile 客户画像
   * @return 错误消息(为空表示校验通过)
   */
  public Optional<String> validate(AnnuityApplicationExtension ext, CustomerProfile profile) {
    if (ext.hasForeignInvestment() && !profile.hasForeignCompany()) {
      return Optional.of("业务声明含外资,但客户画像未关联外资企业,需补充材料");
    }
    if (profile.hasForeignCompany() && !ext.hasForeignInvestment()) {
      return Optional.of("客户画像含外资企业,但业务未声明外资成分,需人工复核");
    }
    return Optional.empty();
  }
}
