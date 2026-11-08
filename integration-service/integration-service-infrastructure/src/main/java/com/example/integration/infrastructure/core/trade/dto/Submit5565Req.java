package com.example.integration.infrastructure.core.trade.dto;

import com.example.integration.infrastructure.core.common.annotation.TradeCode;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

/**
 * 5565 出站文件提交请求
 */
@TradeCode(code = "5565")
public record Submit5565Req(
  @JsonProperty("WJID")
  String fileId,              // 文件ID（必输）

  @JsonProperty("WJMC")
  String fileName,            // 文件名称（可输）

  @JsonProperty("WJLX")
  String fileType,            // 文件类型（可输）

  @JsonProperty("BZ")
  String remark               // 备注（可输）
) implements Serializable {
}
