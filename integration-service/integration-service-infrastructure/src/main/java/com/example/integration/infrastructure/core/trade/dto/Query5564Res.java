package com.example.integration.infrastructure.core.trade.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * 5564 投资组合查询响应体（基础设施层报文定义）
 * 对应 TradeRootResponse<Query5564Res> 中的 body
 */
public record Query5564Res(

  // ========== 分页控制字段 ==========
  @JsonProperty("TotalCount")
  Integer totalCount,             // 总记录数

  @JsonProperty("BeginRecordPos")
  Integer beginRecordPos,         // 起始记录位置

  @JsonProperty("RecordCount")
  Integer recordCount,            // 本次返回记录数

  @JsonProperty("HasMoreFlag")
  String hasMoreFlag,             // 是否有更多：0-无 1-有

  // ========== 查询条件回显 ==========
  @JsonProperty("QYKHH")
  String enterpriseCustomerNo,    // 企业客户号

  @JsonProperty("QYJHBH")
  String enterprisePlanNo,        // 企业计划编号

  // ========== 结果数据（嵌套在 Result 中） ==========
  @JsonProperty("Result")
  Result result

) {
  /**
   * 获取持仓列表（快捷方式）
   */
  public List<PortfolioItem> items() {
    return result != null ? result.items() : List.of();
  }

  /**
   * 是否有更多数据
   */
  public boolean hasMore() {
    return "1".equals(hasMoreFlag);
  }

  // ========== 便捷访问方法 ==========

  /**
   * 结果包装层 - 适配 JSON 中的 Result 对象
   */
  public record Result(
    @JsonProperty("Item")
    List<PortfolioItem> items   // 持仓明细列表
  ) {
  }

  /**
   * 持仓明细项 - 基础设施层报文定义
   * 字段名保持与外部系统一致（拼音缩写）
   */
  public record PortfolioItem(
    // 日期信息
    @JsonProperty("GZRQ")
    String valuationDate,           // 估值日期

    // 账户信息
    @JsonProperty("ZHBH")
    String accountNo,               // 账户编号

    @JsonProperty("ZHMC")
    String accountName,             // 账户名称

    // 投资组合信息
    @JsonProperty("TZZHBH")
    String portfolioNo,             // 投资组合编号

    @JsonProperty("TZZHMC")
    String portfolioName,           // 投资组合名称

    @JsonProperty("TZZHJQWS")
    String portfolioPrecision,      // 投资组合精确位数

    // 封闭期标志
    @JsonProperty("SGFBQBZ")
    String subscriptionLockFlag,    // 申购封闭期标志：0-开放 1-封闭

    @JsonProperty("SHFBQBZ")
    String redemptionLockFlag,      // 赎回封闭期标志：0-开放 1-封闭

    // 份额信息
    @JsonProperty("DQSE")
    BigDecimal currentShares,       // 当前份额

    @JsonProperty("DQKYSE")
    BigDecimal availableShares,     // 可用份额

    @JsonProperty("DQDJSE")
    BigDecimal frozenShares,        // 冻结份额

    // 净值信息
    @JsonProperty("DWJZ")
    BigDecimal netUnitValue,        // 单位净值

    // 市值信息
    @JsonProperty("DQSZ")
    BigDecimal currentMarketValue,      // 当前市值

    @JsonProperty("DQKYSZ")
    BigDecimal availableMarketValue,    // 可用市值

    @JsonProperty("DQDJSZ")
    BigDecimal frozenMarketValue        // 冻结市值
  ) {
  }
}
