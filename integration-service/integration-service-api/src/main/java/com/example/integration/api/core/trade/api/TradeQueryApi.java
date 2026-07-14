package com.example.integration.api.core.trade.api;

import com.example.integration.api.core.trade.dto.PortfolioBalanceDTO;
import com.example.integration.api.core.trade.dto.PortfolioQueryDTO;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/2/5 15:20
 */
@HttpExchange("/api/v1/trade/portfolio")
public interface TradeQueryApi {

  /**
   * 查询投资组合持仓余额（5564）
   */
  @PostExchange("/balance")
  ApiResult<PortfolioBalanceDTO> queryBalance5564(@RequestBody @Valid PortfolioQueryDTO query);
}
