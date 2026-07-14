package com.example.integration.infrastructure.core.trade.dto;

import com.example.integration.infrastructure.core.common.annotation.TradeCode;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * 5564 企业年金计划投资组合查询请求
 */
@TradeCode(code = "5564")
public record Query5564Req(
  @JsonProperty("QYKHH")
  String enterpriseCustomerNo,    // 企业客户号（必输）

  @JsonProperty("QYJHBH")
  String enterprisePlanNo,        // 企业计划编号（必输）

  @JsonProperty("NJJHBH")
  String annuityPlanNo,           // 年金计划编号（必输）

  @JsonProperty("ZHBH")
  String accountNo,               // 账户编号（可输）

  @JsonProperty("BeginRecordPos")
  Integer beginRecordPos,         // 起始记录号（必输）

  @JsonProperty("RecordCount")
  Integer recordCount             // 返回记录数（必输）
) implements Serializable {
}
