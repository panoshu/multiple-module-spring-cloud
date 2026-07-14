package com.example.integration.application.trade.impl;

import com.example.integration.application.trade.service.TradeQueryAppService;
import com.example.integration.domain.trade.gateway.TradeQueryGateway;
import com.example.integration.domain.trade.model.TradePortfolioBalance;
import com.example.integration.domain.trade.model.TradePortfolioQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/2/5 15:55
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeQueryAppServiceImpl implements TradeQueryAppService {
  private final TradeQueryGateway tradeQueryGateway;

  @Override
  @Transactional(readOnly = true)  // 查询也建议加，保持统一事务语义
  public TradePortfolioBalance queryPortfolioBalance5564(TradePortfolioQuery query) {
    // 应用层可以进行：参数校验增强、缓存查询、权限检查、日志记录等
    // 当前直接透传给领域网关

    return tradeQueryGateway.queryPortfolioBalance(query);
  }
}
