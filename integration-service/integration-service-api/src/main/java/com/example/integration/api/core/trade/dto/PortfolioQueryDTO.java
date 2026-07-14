package com.example.integration.api.core.trade.dto;

import com.example.shared.web.core.annotation.BizTag;
import com.example.shared.web.core.dto.PageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 5564 投资组合查询请求（分页复用）
 */
public record PortfolioQueryDTO(
  @NotBlank
  String channel,

  @NotBlank
  String tellerNo,

  @NotBlank(message = "渠道不能为空")
  String tellerName,

  @NotBlank
  @Size(max = 10, message = "企业客户号长度不能超过10")
  @BizTag("UserId")
  String enterpriseCustomerNo,

  @NotBlank
  @Size(max = 10, message = "企业计划号长度不能超过10")
  String enterprisePlanNo,

  @NotBlank
  @Size(max = 6, message = "年金产品编号长度不能超过10")
  String annuityProductNo,

  @Size(max = 4, message = "账户类型长度不能超过10")
  String accountNo,

  @Valid  // 嵌套校验
  PageQuery pageRequest  // 复用统一分页 DTO
) {
  // 便捷工厂方法
  public static PortfolioQueryDTO of(
    String channel, String tellerNo, String tellerName,
    String custNo, String planNo, String annuityNo,
    int startPos, int pageSize
  ) {
    return new PortfolioQueryDTO(
      channel, tellerNo, tellerName,
      custNo, planNo, annuityNo, null,
      PageQuery.of(startPos, pageSize)
    );
  }
}
