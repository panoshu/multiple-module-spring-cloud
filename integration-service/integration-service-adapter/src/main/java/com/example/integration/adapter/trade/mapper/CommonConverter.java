package com.example.integration.adapter.trade.mapper;

import com.example.shared.primitives.page.Pagination;
import com.example.shared.web.core.dto.PageQuery;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * CommonConverter
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/20 22:58
 */
@Mapper
public interface CommonConverter {

  CommonConverter INSTANCE = Mappers.getMapper(CommonConverter.class);

  /**
   * 将 API 请求的分页 DTO 转换为领域模型的分页入参
   */
  Pagination toPagination(PageQuery pageQuery);
}
