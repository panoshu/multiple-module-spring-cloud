package com.example.annuity.domain.extractor;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.annuity.domain.service.AnnuityExtensionResolver;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.annotation.DomainService;
import com.example.core.domain.engine.spi.BusinessFactExtractor;

import java.util.HashMap;
import java.util.Map;

/**
 * 年金业务事实提取器
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@DomainService
public class AnnuityFactExtractor implements BusinessFactExtractor {

  public static final String EXTRACTOR_NAME = "ANNUITY_FACT_EXTRACTOR";

  private static final String FACT_BUSINESS_TYPE = "businessType";
  private static final String FACT_CUSTOMER_NO = "customerNo";
  private static final String FACT_PRODUCT_NO = "productNo";
  private static final String FACT_PLAN_NO = "planNo";
  private static final String FACT_PLAN_TYPE = "planType";
  private static final String FACT_INITIAL_CONTRIBUTION = "initialContribution";
  private static final String FACT_HAS_FOREIGN_INVESTMENT = "hasForeignInvestment";

  private final AnnuityExtensionResolver extensionResolver;

  public AnnuityFactExtractor(AnnuityExtensionResolver extensionResolver) {
    this.extensionResolver = extensionResolver;
  }

  @Override
  public String extractorName() {
    return EXTRACTOR_NAME;
  }

  @Override
  public Map<String, Object> extractBusinessFacts(BusinessApplication businessApplication) {
    Map<String, Object> facts = new HashMap<>();

    BusinessMetaContext metaContext = businessApplication.buildConfigQueryContext();
    if (metaContext != null) {
      putIfNotNull(facts, FACT_BUSINESS_TYPE,
          metaContext.businessType() != null ? metaContext.businessType().name() : null);
      putIfNotNull(facts, FACT_CUSTOMER_NO,
          metaContext.customerNo() != null ? metaContext.customerNo().value() : null);
      putIfNotNull(facts, FACT_PRODUCT_NO,
          metaContext.productNo() != null ? metaContext.productNo().value() : null);
      putIfNotNull(facts, FACT_PLAN_NO,
          metaContext.planNo() != null ? metaContext.planNo().value() : null);
    }

    AnnuityApplicationExtension ext = extensionResolver.resolve(businessApplication);
    putIfNotNull(facts, FACT_PLAN_TYPE, ext.planType());
    putIfNotNull(facts, FACT_INITIAL_CONTRIBUTION, ext.initialContribution());
    facts.put(FACT_HAS_FOREIGN_INVESTMENT, ext.hasForeignInvestment());

    return facts;
  }

  private static void putIfNotNull(Map<String, Object> facts, String key, Object value) {
    if (value != null) {
      facts.put(key, value);
    }
  }
}
