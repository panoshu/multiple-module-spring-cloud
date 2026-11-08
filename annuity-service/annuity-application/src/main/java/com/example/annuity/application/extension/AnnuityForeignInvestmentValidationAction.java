package com.example.annuity.application.extension;

import com.example.annuity.domain.aggregate.valueobject.CustomerProfile;
import com.example.annuity.domain.gateway.AnnuityCustomerGateway;
import com.example.annuity.domain.service.AnnuityExtensionResolver;
import com.example.annuity.domain.service.AnnuityForeignInvestmentRule;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.engine.spi.StepExtensionAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 年金外资准入校验扩展动作
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Component("annuityForeignInvestmentValidationAction")
@RequiredArgsConstructor
public class AnnuityForeignInvestmentValidationAction implements StepExtensionAction {

  private final AnnuityExtensionResolver extensionResolver;
  private final AnnuityForeignInvestmentRule foreignInvestmentRule;
  private final AnnuityCustomerGateway customerGateway;

  @Override
  public String actionName() {
    return "annuityForeignInvestmentValidationAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    CustomerProfile profile = customerGateway.queryCustomer(app.businessContext().customerNo());
    return foreignInvestmentRule.validate(extensionResolver.resolve(app), profile)
      .map(msg -> ExtensionExecutionResult.failure("FOREIGN_INVESTMENT_BLOCKED", msg))
      .orElseGet(ExtensionExecutionResult::success);
  }
}
