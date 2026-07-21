package com.example.annuity.domain.extractor;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessExtension;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.annotation.DomainService;
import com.example.core.domain.spi.BusinessFactExtractor;

import java.util.HashMap;
import java.util.Map;

/**
 * 年金业务事实提取器
 * <p>
 * 实现 kernel 的 {@link BusinessFactExtractor} SPI，从 {@link BusinessApplication} 聚合根中
 * 提取年金业务专属事实，供 kernel 规则引擎（{@code BusinessConfigGateway}）做条件路由。
 * <p>
 * 提取的事实键：
 * <ul>
 *   <li>{@code businessType} - 业务类型（来自 BusinessContext，kernel 公开 API）</li>
 *   <li>{@code customerNo} / {@code productNo} / {@code planNo} - 业务上下文维度</li>
 *   <li>{@code planType} - 年金计划操作类型（NEW/MODIFY/DELETE，来自扩展字段）</li>
 *   <li>{@code initialContribution} - 初始缴费金额（分，来自扩展字段）</li>
 *   <li>{@code hasForeignInvestment} - 是否含外资（来自扩展字段，影响审批路径）</li>
 * </ul>
 * <p>
 * 注意：kernel 的 {@code BusinessApplication.businessExtension} 字段当前无公开 getter，
 * 本提取器通过反射读取以演示完整 SPI 实现；待 kernel 后续开放公开 accessor 后可移除反射。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@DomainService
public class AnnuityFactExtractor implements BusinessFactExtractor {

  /**
   * 提取器名称，需与配置中心 {@code ExtractorConfig.extractorName} 一致
   */
  public static final String EXTRACTOR_NAME = "ANNUITY_FACT_EXTRACTOR";

  private static final String FACT_BUSINESS_TYPE = "businessType";
  private static final String FACT_CUSTOMER_NO = "customerNo";
  private static final String FACT_PRODUCT_NO = "productNo";
  private static final String FACT_PLAN_NO = "planNo";
  private static final String FACT_PLAN_TYPE = "planType";
  private static final String FACT_INITIAL_CONTRIBUTION = "initialContribution";
  private static final String FACT_HAS_FOREIGN_INVESTMENT = "hasForeignInvestment";

  @Override
  public String extractorName() {
    return EXTRACTOR_NAME;
  }

  @Override
  public Map<String, Object> extractBusinessFacts(BusinessApplication businessApplication) {
    Map<String, Object> facts = new HashMap<>();

    // 1. 从 BusinessContext（公开 API）提取基础事实
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

    // 2. 从 BusinessExtension 提取年金专属事实
    BusinessExtension extension = readExtension(businessApplication);
    if (extension instanceof AnnuityApplicationExtension annuityExt) {
      putIfNotNull(facts, FACT_PLAN_TYPE, annuityExt.planType());
      putIfNotNull(facts, FACT_INITIAL_CONTRIBUTION, annuityExt.initialContribution());
      facts.put(FACT_HAS_FOREIGN_INVESTMENT, annuityExt.hasForeignInvestment());
    }

    return facts;
  }

  /**
   * 通过反射读取 BusinessApplication.businessExtension 私有字段。
   * kernel 当前未提供公开 getter，此处仅作演示用途。
   */
  private BusinessExtension readExtension(BusinessApplication app) {
    if (app == null) {
      return null;
    }
    try {
      var field = BusinessApplication.class.getDeclaredField("businessExtension");
      field.setAccessible(true);
      return (BusinessExtension) field.get(app);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      return null;
    }
  }

  private static void putIfNotNull(Map<String, Object> facts, String key, Object value) {
    if (value != null) {
      facts.put(key, value);
    }
  }
}
