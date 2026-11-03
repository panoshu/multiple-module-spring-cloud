package com.example.integration.infrastructure.core.trade.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * 5565 出站文件提交响应体
 */
public record Submit5565Res(
  @JsonProperty("WJID")
  String fileId,              // 文件ID

  @JsonProperty("JYLSH")
  String transactionSerialNo, // 交易流水号

  @JsonProperty("CLJG")
  String processResult,       // 处理结果：0-成功 1-失败

  @JsonProperty("CLXX")
  String processMessage       // 处理信息
) implements Serializable {

  /**
   * 是否处理成功
   */
  public boolean isProcessedSuccessfully() {
    return "0".equals(processResult);
  }
}
