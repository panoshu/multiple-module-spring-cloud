package com.example.annuity.application.extension;

import com.example.annuity.domain.gateway.AnnuityCustomerGateway;
import com.example.annuity.domain.aggregate.valueobject.CustomerProfile;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.ExtensionExecutionResult;
import com.example.core.domain.engine.spi.StepExtensionAction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 年金客户画像丰富扩展动作
 * <p>
 * 调用外部客户接口获取画像,通过 mutations 向 context 追加客户风险等级与关联企业信息。
 *
 * @author annuity-service
 * @since 2026/7/22
 */
@Component("annuityCustomerProfileEnrichmentAction")
@RequiredArgsConstructor
public class AnnuityCustomerProfileEnrichmentAction implements StepExtensionAction {

  private final AnnuityCustomerGateway customerGateway;

  @Override
  public String actionName() {
    return "annuityCustomerProfileEnrichmentAction";
  }

  @Override
  public ExtensionExecutionResult execute(BusinessApplication app, BusinessMetaContext context, Map<String, Object> params) {
    CustomerProfile profile = customerGateway.queryCustomer(app.businessContext().customerNo());
    Map<String, Object> mutations = new HashMap<>();
    mutations.put("customerRiskLevel", profile.riskLevel());
    mutations.put("customerRelatedCompanies", profile.relatedCompanies());
    return ExtensionExecutionResult.success(mutations);
  }
}
