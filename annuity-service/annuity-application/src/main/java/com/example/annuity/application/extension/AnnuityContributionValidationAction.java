package com.example.annuity.application.extension;

import com.example.annuity.domain.service.AnnuityContributionRule;
import com.example.annuity.domain.service.AnnuityExtensionResolver;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.spi.StepExtensionAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 年金缴费金额校验扩展动作
 * <p>
 * 委托 {@link AnnuityContributionRule} 执行纯领域规则,自身只做结果转换。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Component("annuityContributionValidationAction")
@RequiredArgsConstructor
public class AnnuityContributionValidationAction implements StepExtensionAction {

  private final AnnuityExtensionResolver extensionResolver;
  private final AnnuityContributionRule contributionRule;

  @Override
  public String actionName() {
    return "annuityContributionValidationAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    return contributionRule.validate(extensionResolver.resolve(app))
        .map(msg -> ExtensionExecutionResult.failure("INVALID_CONTRIBUTION", msg))
        .orElseGet(ExtensionExecutionResult::success);
  }
}
