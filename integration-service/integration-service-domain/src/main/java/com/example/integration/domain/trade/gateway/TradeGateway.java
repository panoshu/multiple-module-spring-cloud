package com.example.integration.domain.trade.gateway;

import com.example.integration.domain.trade.model.TradePortfolioBalance;
import com.example.integration.domain.trade.model.TradePortfolioQuery;

/**
 * TradeGateway
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/2/4 22:31
 */
public interface TradeGateway {

  // 新增 5564
  TradePortfolioBalance queryPortfolioBalance(TradePortfolioQuery query);
}
