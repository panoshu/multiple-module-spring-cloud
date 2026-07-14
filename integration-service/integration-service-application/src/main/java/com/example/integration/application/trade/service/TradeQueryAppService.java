package com.example.integration.application.trade.service;

import com.example.integration.domain.trade.model.TradePortfolioBalance;
import com.example.integration.domain.trade.model.TradePortfolioQuery;

/**
 * Trade 查询服务
 */
public interface TradeQueryAppService {

  /**
   * 查询投资组合持仓
   */
  TradePortfolioBalance queryPortfolioBalance5564(TradePortfolioQuery query);
}
