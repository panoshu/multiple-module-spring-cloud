package com.example.integration.application.trade.impl;

import com.example.integration.application.trade.service.TradeSubmissionAppService;
import com.example.integration.domain.trade.gateway.FileSubmissionGateway;
import com.example.integration.domain.trade.model.FileSubmissionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 文件提交应用服务实现
 * <p>
 * 应用层职责：参数校验、事务管理、编排领域网关调用。
 * 不包含业务规则，业务规则由领域层（Gateway + Domain Model）承载。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeSubmissionAppServiceImpl implements TradeSubmissionAppService {

  private final FileSubmissionGateway fileSubmissionGateway;

  @Override
  @Transactional
  public FileSubmissionResult submitOutboundFile(String fileId, String tradeCode, Map<String, String> headParams) {
    log.info("提交出站文件, fileId={}, tradeCode={}", fileId, tradeCode);
    return fileSubmissionGateway.submit(fileId, tradeCode, headParams);
  }
}
