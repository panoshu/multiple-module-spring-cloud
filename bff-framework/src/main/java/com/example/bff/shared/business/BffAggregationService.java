package com.example.bff.shared.business;

import com.example.bff.shared.business.dto.BatchOverviewResponse;
import com.example.bff.shared.business.dto.BffBatchOverviewRequest;
import com.example.shared.web.core.api.ApiResult;

/**
 * BFF 聚合编排服务（互联网/内网共用）
 *
 * <p>纯委托角色：根据请求中的 {@code businessType} 查找
 * {@link BusinessScenarioHandler} 并调用聚合方法。
 *
 * @author bff
 */
@org.springframework.stereotype.Service
public class BffAggregationService {

  private final ScenarioHandlerRegistry registry;

  public BffAggregationService(ScenarioHandlerRegistry registry) {
    this.registry = registry;
  }

  /**
   * 获取批次概览：聚合批次详情 + 进度 + 申请单列表。
   *
   * <p>具体聚合策略由各 {@link BusinessScenarioHandler#batchOverview} 实现，
   * 默认回退到批次详情（无进度/申请单数据）。
   */
  public ApiResult<BatchOverviewResponse> getBatchOverview(BffBatchOverviewRequest request) {
    return registry.handlerFor(request.businessType()).batchOverview(request);
  }
}
