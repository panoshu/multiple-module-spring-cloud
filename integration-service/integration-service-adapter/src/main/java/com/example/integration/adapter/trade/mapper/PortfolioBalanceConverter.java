package com.example.integration.adapter.trade.mapper;

import com.example.integration.api.core.trade.dto.PortfolioBalanceDTO;
import com.example.integration.domain.trade.model.TradePortfolioBalance;
import com.example.shared.web.core.dto.PageData;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * PortfolioBalanceConverter
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/20 23:01
 */
@Mapper
public interface PortfolioBalanceConverter {

  PortfolioBalanceConverter INSTANCE = Mappers.getMapper(PortfolioBalanceConverter.class);

  /**
   * 1. 核心入口
   *
   * <p>source = "domain" 指向 toDtoBalance 的参数名，MapStruct 会将整个 TradePortfolioBalance
   * 传给 {@link #toPageData(TradePortfolioBalance)} 进行桥接组装。
   */
  @Mapping(target = "pageResult", source = "domain", qualifiedByName = "domainToPageData")
  PortfolioBalanceDTO toDtoBalance(TradePortfolioBalance domain);

  /**
   * 2. 桥接组装：返回值修复为正确的公共泛型 PageData
   */
  @Named("domainToPageData")
  @Mapping(target = "totalCount", source = "pageInfo.totalCount")
  @Mapping(target = "currentStart", source = "pageInfo.currentStart")
  @Mapping(target = "returnedCount", source = "pageInfo.returnedCount")
  @Mapping(target = "hasMore", source = "pageInfo.hasMore")
  @Mapping(target = "items", source = "items")
  PageData<PortfolioBalanceDTO.PortfolioItemDTO> toPageData(TradePortfolioBalance domain);

  /**
   * 3. 集合转换
   */
  List<PortfolioBalanceDTO.PortfolioItemDTO> toItemDtoList(List<TradePortfolioBalance.PortfolioItem> items);

  /**
   * 4. 元素转换
   */
  @Mapping(target = "canSubscribe", expression = "java(item.canSubscribe())")
  @Mapping(target = "canRedeem", expression = "java(item.canRedeem())")
  PortfolioBalanceDTO.PortfolioItemDTO toItemDto(TradePortfolioBalance.PortfolioItem item);

  // 5. 子对象转换
  PortfolioBalanceDTO.LockStatusDTO toLockStatusDto(TradePortfolioBalance.LockStatus status);

  PortfolioBalanceDTO.SharesInfoDTO toSharesInfoDto(TradePortfolioBalance.SharesInfo shares);

  PortfolioBalanceDTO.NetValueInfoDTO toNetValueInfoDto(TradePortfolioBalance.NetValueInfo netValue);

  PortfolioBalanceDTO.MarketValueInfoDTO toMarketValueInfoDto(TradePortfolioBalance.MarketValueInfo marketValue);
}
