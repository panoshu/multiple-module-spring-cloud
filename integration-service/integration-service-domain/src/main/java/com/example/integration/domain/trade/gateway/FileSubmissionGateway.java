package com.example.integration.domain.trade.gateway;

import com.example.integration.domain.trade.model.FileSubmissionResult;

/**
 * 文件提交网关 SPI
 * <p>
 * 负责将出站文件提交至外部交易系统。
 * 实现类在 infrastructure 层，通过 BaseTradeClient 调用外部交易接口。
 */
public interface FileSubmissionGateway {

  /**
   * 提交出站文件至外部交易系统
   *
   * @param fileId      file-service 中已生成的出站文件 ID（数据凭证）
   * @param tradeCode   目标交易码（如 "5565"）
   * @param headParams  交易头扩展参数（柜员号、渠道等）
   * @return 提交结果
   */
  FileSubmissionResult submit(String fileId, String tradeCode, java.util.Map<String, String> headParams);
}
