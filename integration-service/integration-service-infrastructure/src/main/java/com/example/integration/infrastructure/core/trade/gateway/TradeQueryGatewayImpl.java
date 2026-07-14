package com.example.integration.infrastructure.core.trade.gateway;

import com.example.integration.domain.trade.gateway.TradeQueryGateway;
import com.example.integration.domain.trade.model.TradePortfolioBalance;
import com.example.integration.domain.trade.model.TradePortfolioQuery;
import com.example.integration.infrastructure.core.common.gateway.BaseTradeGateway;
import com.example.integration.infrastructure.core.trade.dto.Query5564Req;
import com.example.integration.infrastructure.core.trade.dto.Query5564Res;
import com.example.integration.infrastructure.core.trade.mapper.TradePortfolioInfraMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * TradeQueryGatewayImpl
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/2/4 22:43
 */
@Component
public class TradeQueryGatewayImpl extends BaseTradeGateway implements TradeQueryGateway {

  private final TradePortfolioInfraMapper tradePortfolioInfraMapper;

  public TradeQueryGatewayImpl(ObjectMapper objectMapper, TradeQueryClient tradeQueryClient, TradePortfolioInfraMapper tradePortfolioInfraMapper) {
    super(objectMapper, tradeQueryClient);
    this.tradePortfolioInfraMapper = tradePortfolioInfraMapper;
  }

  @Override
  public TradePortfolioBalance queryPortfolioBalance(TradePortfolioQuery query) {
    // 1. Domain -> Infra DTO
    Query5564Req reqBody = tradePortfolioInfraMapper.to5564Req(query);

    return performTradeCall(
      reqBody,
      head -> head
        .withTeller(query.tellerNo(), query.tellerName())
        .withChannel(query.channel()),
      Query5564Res.class,
      tradePortfolioInfraMapper::toBalance
    );
  }
}
