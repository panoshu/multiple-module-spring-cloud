package com.example.integration.application.trade.service;

import com.example.integration.domain.trade.model.FileSubmissionResult;

import java.util.Map;

/**
 * 文件提交应用服务
 * <p>
 * 协调文件提交网关调用，管理事务边界。
 */
public interface TradeSubmissionAppService {

  /**
   * 提交出站文件至外部交易系统
   *
   * @param fileId     file-service 中已生成的出站文件 ID
   * @param tradeCode  目标交易码
   * @param headParams 交易头扩展参数
   * @return 提交结果
   */
  FileSubmissionResult submitOutboundFile(String fileId, String tradeCode, Map<String, String> headParams);
}
