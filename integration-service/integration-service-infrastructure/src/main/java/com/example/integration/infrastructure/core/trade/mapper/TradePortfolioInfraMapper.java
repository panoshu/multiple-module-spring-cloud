package com.example.integration.infrastructure.core.trade.mapper;

import com.example.integration.domain.trade.model.TradePortfolioBalance;
import com.example.integration.domain.trade.model.TradePortfolioQuery;
import com.example.integration.infrastructure.core.trade.dto.Query5564Req;
import com.example.integration.infrastructure.core.trade.dto.Query5564Res;
import com.example.shared.page.PageInfo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 交易投资组合基础设施层转换器
 * 设计原则：
 * 1. 简单字段映射使用 MapStruct 注解自动生成
 * 2. 复杂内聚对象（VO）构建使用 @Named 方法
 * 3. 空值安全和默认值处理统一化
 * 4. 分页逻辑完整封装（当前页、总页数计算）
 *
 * @author hupan
 * @since 2026/2/7
 */
@Mapper(
  componentModel = "spring",
  unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface TradePortfolioInfraMapper {

  // ========== 请求转换：Domain Query -> Infra DTO ==========

  @Mapping(source = "enterpriseCustomerNo", target = "enterpriseCustomerNo")
  @Mapping(source = "enterprisePlanNo", target = "enterprisePlanNo")
  @Mapping(source = "annuityPlanNo", target = "annuityPlanNo")
  @Mapping(source = "accountNo", target = "accountNo")
  @Mapping(source = "pagination.startPos", target = "beginRecordPos")
  @Mapping(source = "pagination.pageSize", target = "recordCount")
  Query5564Req to5564Req(TradePortfolioQuery query);

  // ========== 响应转换：Infra DTO -> Domain Model ==========


  /**
   * 转换为领域模型（主入口）
   */
  default TradePortfolioBalance toBalance(Query5564Res res) {
    if (res == null) {
      return emptyBalance();
    }

    return new TradePortfolioBalance(
      buildPageInfo(res),
      buildPortfolioItems(res.items())
    );
  }

  // ========== 分页信息构建 ==========

  /**
   * 从响应构建完整的分页信息
   */
  @Named("buildPageInfo")
  default PageInfo buildPageInfo(Query5564Res res) {
    if (res == null) {
      return PageInfo.empty();
    }

    int totalCount = defaultInt(res.totalCount());
    int returnedCount = defaultInt(res.recordCount());
    int startPos = defaultInt(res.beginRecordPos());
    boolean hasMore = "1".equals(res.hasMoreFlag());

    // 使用 PageInfo 的工厂方法
    return new PageInfo(totalCount, startPos, returnedCount, hasMore);
  }

  // ========== 持仓明细列表构建 ==========

  /**
   * 构建持仓明细列表（空安全）
   */
  default List<TradePortfolioBalance.PortfolioItem> buildPortfolioItems(List<Query5564Res.PortfolioItem> items) {
    if (items == null || items.isEmpty()) {
      return Collections.emptyList();
    }

    return items.stream()
      .filter(Objects::nonNull)
      .map(this::toPortfolioItem)
      .collect(Collectors.toList());
  }

  /**
   * 转换单个持仓明细
   */
  default TradePortfolioBalance.PortfolioItem toPortfolioItem(Query5564Res.PortfolioItem item) {
    if (item == null) {
      return null;
    }

    return new TradePortfolioBalance.PortfolioItem(
      item.accountNo(),
      item.accountName(),
      item.portfolioNo(),
      item.portfolioName(),
      item.valuationDate(),
      buildLockStatus(item),
      buildSharesInfo(item),
      buildNetValueInfo(item),
      buildMarketValueInfo(item)
    );
  }

  // ========== 内聚对象构建 ==========

  /**
   * 构建封闭期状态
   * 规则："0"=开放(false)，其他=封闭(true)
   */
  @Named("buildLockStatus")
  default TradePortfolioBalance.LockStatus buildLockStatus(Query5564Res.PortfolioItem item) {
    if (item == null) {
      return new TradePortfolioBalance.LockStatus(false, false);
    }

    return new TradePortfolioBalance.LockStatus(
      !"0".equals(item.subscriptionLockFlag()),
      !"0".equals(item.redemptionLockFlag())
    );
  }

  /**
   * 构建份额信息
   */
  @Named("buildSharesInfo")
  default TradePortfolioBalance.SharesInfo buildSharesInfo(Query5564Res.PortfolioItem item) {
    if (item == null) {
      return new TradePortfolioBalance.SharesInfo(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    return new TradePortfolioBalance.SharesInfo(
      defaultZero(item.currentShares()),
      defaultZero(item.availableShares()),
      defaultZero(item.frozenShares())
    );
  }

  /**
   * 构建净值信息
   * 注意：Query5564Res 中没有 navDate 字段，复用 valuationDate
   */
  @Named("buildNetValueInfo")
  default TradePortfolioBalance.NetValueInfo buildNetValueInfo(Query5564Res.PortfolioItem item) {
    if (item == null) {
      return new TradePortfolioBalance.NetValueInfo(BigDecimal.ZERO, null);
    }

    // JSON 报文中没有独立的净值日期字段，复用估值日期
    return new TradePortfolioBalance.NetValueInfo(
      defaultZero(item.netUnitValue()),
      item.valuationDate()
    );
  }

  /**
   * 构建市值信息
   */
  @Named("buildMarketValueInfo")
  default TradePortfolioBalance.MarketValueInfo buildMarketValueInfo(Query5564Res.PortfolioItem item) {
    if (item == null) {
      return new TradePortfolioBalance.MarketValueInfo(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    return new TradePortfolioBalance.MarketValueInfo(
      defaultZero(item.currentMarketValue()),
      defaultZero(item.availableMarketValue()),
      defaultZero(item.frozenMarketValue())
    );
  }

  // ========== 反向转换（缓存/同步场景）==========

  /**
   * 领域模型 -> 基础设施响应（用于缓存或同步）
   */
  default Query5564Res toRes(TradePortfolioBalance balance) {
    if (balance == null) {
      return null;
    }

    PageInfo pageInfo = balance.pageInfo();

    return new Query5564Res(
      pageInfo != null ? pageInfo.totalCount() : 0,
      pageInfo != null ? pageInfo.currentStart() : 0,
      pageInfo != null ? pageInfo.returnedCount() : 0,
      pageInfo != null && pageInfo.hasMore() ? "1" : "0",
      null,  // enterpriseCustomerNo - 领域模型中无
      null,  // enterprisePlanNo - 领域模型中无
      new Query5564Res.Result(toInfraItems(balance.items()))
    );
  }

  default List<Query5564Res.PortfolioItem> toInfraItems(List<TradePortfolioBalance.PortfolioItem> items) {
    if (items == null) {
      return Collections.emptyList();
    }

    return items.stream()
      .filter(Objects::nonNull)
      .map(this::toInfraItem)
      .collect(Collectors.toList());
  }

  default Query5564Res.PortfolioItem toInfraItem(TradePortfolioBalance.PortfolioItem item) {
    if (item == null) {
      return null;
    }

    TradePortfolioBalance.LockStatus lockStatus = item.lockStatus();
    TradePortfolioBalance.SharesInfo shares = item.shares();
    TradePortfolioBalance.NetValueInfo netValue = item.netValue();
    TradePortfolioBalance.MarketValueInfo marketValue = item.marketValue();

    return new Query5564Res.PortfolioItem(
      item.valuationDate(),      // 1. valuationDate
      item.accountNo(),          // 2. accountNo
      item.accountName(),        // 3. accountName
      item.portfolioNo(),        // 4. portfolioNo
      item.portfolioName(),      // 5. portfolioName
      null,                      // 6. portfolioPrecision - 领域模型中无
      toLockFlag(lockStatus != null ? lockStatus.subscriptionLocked() : false),  // 7. subscriptionLockFlag
      toLockFlag(lockStatus != null ? lockStatus.redemptionLocked() : false),    // 8. redemptionLockFlag
      shares != null ? shares.current() : BigDecimal.ZERO,       // 9. currentShares
      shares != null ? shares.available() : BigDecimal.ZERO,     // 10. availableShares
      shares != null ? shares.frozen() : BigDecimal.ZERO,        // 11. frozenShares
      netValue != null ? netValue.unitValue() : BigDecimal.ZERO, // 12. netUnitValue
      marketValue != null ? marketValue.total() : BigDecimal.ZERO,       // 13. currentMarketValue
      marketValue != null ? marketValue.available() : BigDecimal.ZERO,   // 14. availableMarketValue
      marketValue != null ? marketValue.frozen() : BigDecimal.ZERO       // 15. frozenMarketValue
    );
  }

  // ========== 工具方法 ==========

  default String toLockFlag(boolean locked) {
    return locked ? "1" : "0";
  }

  default BigDecimal defaultZero(BigDecimal val) {
    return val != null ? val : BigDecimal.ZERO;
  }

  default int defaultInt(Integer val) {
    return val != null ? val : 0;
  }

  default TradePortfolioBalance emptyBalance() {
    return new TradePortfolioBalance(PageInfo.empty(), Collections.emptyList());
  }
}
