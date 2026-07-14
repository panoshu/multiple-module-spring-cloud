package com.example.integration.domain.trade.model;

import com.example.shared.primitives.page.PageInfo;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * 投资组合持仓查询结果（领域模型）
 */
public record TradePortfolioBalance(
  PageInfo pageInfo,                    // 分页信息内聚
  List<PortfolioItem> items
) implements Serializable {
  // 便捷方法：获取总持仓市值
  public BigDecimal totalMarketValue() {
    return items.stream()
      .map(PortfolioItem::marketValue)
      .map(MarketValueInfo::total)
      .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  /**
   * 投资组合持仓明细
   */
  public record PortfolioItem(
    // 账户标识
    String accountNo,           // 账户编号
    String accountName,         // 账户名称
    String portfolioNo,         // 投资组合编号
    String portfolioName,       // 投资组合名称

    // 日期信息
    String valuationDate,       // 估值日期

    // 状态标志
    LockStatus lockStatus,      // 封闭期状态（内聚为对象）

    // 份额信息（单位：份）
    SharesInfo shares,          // 份额信息（内聚为对象）

    // 净值信息
    NetValueInfo netValue,      // 净值信息（内聚为对象）

    // 市值信息（单位：元）
    MarketValueInfo marketValue // 市值信息（内聚为对象）
  ) implements Serializable {

    /**
     * 计算持仓收益率（需要历史成本时扩展）
     */
    public BigDecimal calculateProfitRate(BigDecimal costValue) {
      if (costValue == null || costValue.compareTo(BigDecimal.ZERO) == 0) {
        return BigDecimal.ZERO;
      }
      return marketValue.total()
        .subtract(costValue)
        .divide(costValue, 4, RoundingMode.HALF_UP)
        .multiply(new BigDecimal("100"));
    }

    /**
     * 是否可申购
     */
    public boolean canSubscribe() {
      return !lockStatus.subscriptionLocked();
    }

    /**
     * 是否可赎回
     */
    public boolean canRedeem() {
      return !lockStatus.redemptionLocked()
        && shares.available().compareTo(BigDecimal.ZERO) > 0;
    }
  }

  /**
   * 封闭期状态
   */
  public record LockStatus(
    boolean subscriptionLocked,   // 申购封闭
    boolean redemptionLocked      // 赎回封闭
  ) implements Serializable {
  }

  /**
   * 份额信息
   */
  public record SharesInfo(
    BigDecimal current,     // 当前份额
    BigDecimal available,   // 可用份额
    BigDecimal frozen       // 冻结份额
  ) implements Serializable {
    public SharesInfo {
      // 校验：当前 = 可用 + 冻结
      if (current != null && available != null && frozen != null) {
        var sum = available.add(frozen);
        if (current.compareTo(sum) != 0) {
          throw new IllegalArgumentException(
            "份额校验失败: 当前份额(%s) != 可用(%s) + 冻结(%s)"
              .formatted(current, available, frozen)
          );
        }
      }
    }
  }

  /**
   * 净值信息
   */
  public record NetValueInfo(
    BigDecimal unitValue,   // 单位净值
    String navDate          // 净值日期
  ) implements Serializable {
  }

  /**
   * 市值信息
   */
  public record MarketValueInfo(
    BigDecimal total,       // 总市值
    BigDecimal available,   // 可用市值
    BigDecimal frozen       // 冻结市值
  ) implements Serializable {
    public BigDecimal total() {
      // 如果 total 为 null，尝试计算
      return total != null ? total :
        Optional.ofNullable(available).orElse(BigDecimal.ZERO)
          .add(Optional.ofNullable(frozen).orElse(BigDecimal.ZERO));
    }
  }
}
