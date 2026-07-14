package com.example.integration.domain.trade.model;

import com.example.shared.primitives.page.Pagination;

import java.io.Serializable;

/**
 * 5564 投资组合持仓查询条件（领域模型）
 */
public record TradePortfolioQuery(
  // 操作员信息
  String channel,
  String tellerNo,
  String tellerName,

  // 客户计划信息
  String enterpriseCustomerNo, // 企业客户号
  String enterprisePlanNo,     // 企业计划编号
  String annuityPlanNo,        // 年金计划编号

  // 可选过滤条件
  String accountNo,            // 账户编号（可选）

  // 分页参数
  Pagination pagination
) implements Serializable {

  public TradePortfolioQuery {
    // 默认分页
    if (pagination == null) {
      pagination = new Pagination(0, 20);
    }
  }


  // 工厂方法
  public static TradePortfolioQuery of(
    String channel, String tellerNo, String tellerName,
    String custNo, String planNo, String annuityNo
  ) {
    return new TradePortfolioQuery(
      channel, tellerNo, tellerName,
      custNo, planNo, annuityNo,
      null, new Pagination(0, 20)
    );
  }

  public TradePortfolioQuery withAccount(String accountNo) {
    return new TradePortfolioQuery(
      this.channel, this.tellerNo, this.tellerName,
      this.enterpriseCustomerNo, this.enterprisePlanNo, this.annuityPlanNo,
      accountNo, this.pagination
    );
  }

  public TradePortfolioQuery nextPage() {
    return new TradePortfolioQuery(
      this.channel, this.tellerNo, this.tellerName,
      this.enterpriseCustomerNo, this.enterprisePlanNo, this.annuityPlanNo,
      this.accountNo,
      this.pagination.next()
    );
  }
}
