package com.example.integration.adapter.trade;

import com.example.auth.api.annotation.PermissionCategory;
import com.example.auth.api.annotation.RequirePermission;
import com.example.integration.adapter.trade.mapper.PortfolioBalanceConverter;
import com.example.integration.adapter.trade.mapper.PortfolioConverter;
import com.example.integration.api.core.trade.api.TradeQueryApi;
import com.example.integration.api.core.trade.dto.PortfolioBalanceDTO;
import com.example.integration.api.core.trade.dto.PortfolioQueryDTO;
import com.example.integration.application.trade.service.TradeQueryAppService;
import com.example.integration.domain.trade.model.TradePortfolioBalance;
import com.example.integration.domain.trade.model.TradePortfolioQuery;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/2/5 15:58
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TradeQueryAdapter implements TradeQueryApi {

  private final TradeQueryAppService queryService;
  private final PortfolioConverter portfolioConverter;
  private final PortfolioBalanceConverter portfolioBalanceConverter;

  @Override
  @RequirePermission(business = "TRADE_QUERY", action = "QUERY_BALANCE", category = PermissionCategory.PLATFORM)
  public ApiResult<PortfolioBalanceDTO> queryBalance5564(PortfolioQueryDTO queryDTO) {
    // 1. DTO -> Domain Model
    TradePortfolioQuery domainQuery = portfolioConverter.toDomain(queryDTO);
    log.info("queryBalance5564 queryDTO={}", queryDTO);
    log.info("queryBalance5564 domainQuery={}", domainQuery);

    // 2. 调用应用服务
    TradePortfolioBalance domainResult = queryService.queryPortfolioBalance5564(domainQuery);

    // 3. Domain Model -> DTO
    PortfolioBalanceDTO dtoResult = portfolioBalanceConverter.toDtoBalance(domainResult);

    // 4. 包装统一响应
    return ApiResult.success(dtoResult);
  }
}
