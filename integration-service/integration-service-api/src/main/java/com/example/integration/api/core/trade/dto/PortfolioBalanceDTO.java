package com.example.integration.api.core.trade.dto;

import com.example.shared.web.core.dto.PageData;

import java.math.BigDecimal;

/**
 * 5564 投资组合查询响应（分页复用）
 */
public record PortfolioBalanceDTO(
  // 直接复用统一分页响应
  PageData<PortfolioItemDTO> pageResult
) {
  // 嵌套 item 定义
  public record PortfolioItemDTO(
    String accountNo,
    String accountName,
    String portfolioNo,
    String portfolioName,
    String valuationDate,
    LockStatusDTO lockStatus,
    SharesInfoDTO shares,
    NetValueInfoDTO netValue,
    MarketValueInfoDTO marketValue,
    boolean canSubscribe,
    boolean canRedeem
  ) {
  }

  public record LockStatusDTO(boolean subscriptionLocked, boolean redemptionLocked) {
  }

  public record SharesInfoDTO(BigDecimal current, BigDecimal available, BigDecimal frozen) {
  }

  public record NetValueInfoDTO(BigDecimal unitValue, String navDate) {
  }

  public record MarketValueInfoDTO(BigDecimal total, BigDecimal available, BigDecimal frozen) {
  }
}
