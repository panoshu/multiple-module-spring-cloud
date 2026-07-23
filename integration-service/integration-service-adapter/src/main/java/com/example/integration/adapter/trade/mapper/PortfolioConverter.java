package com.example.integration.adapter.trade.mapper;

import com.example.integration.api.core.trade.dto.PortfolioQueryDTO;
import com.example.integration.domain.trade.model.TradePortfolioQuery;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * PortfolioConverter
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/20 22:59
 */
@Mapper(componentModel = "spring", uses = CommonConverter.class) // 引入通用转换器
public interface PortfolioConverter {

  /**
   * 从 API 查询 DTO 映射到领域查询模型
   * <p>
   * 说明：
   * 1. annuityProductNo 映射到 annuityPlanNo（名称不一致，需显式声明）
   * 2. pageRequest 映射到 pagination（由于引入了 CommonConverter，MapStruct 会自动调用其 toPagination 方法）
   */
  @Mapping(target = "annuityPlanNo", source = "annuityProductNo")
  @Mapping(target = "pagination", source = "pageRequest")
  @Mapping(target = "withAccount", ignore = true)
  TradePortfolioQuery toDomain(PortfolioQueryDTO dto);
}
