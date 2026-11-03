package com.example.integration.infrastructure.core.trade.gateway;

import com.example.integration.domain.trade.gateway.FileSubmissionGateway;
import com.example.integration.domain.trade.model.FileSubmissionResult;
import com.example.integration.infrastructure.core.common.adapter.TradeQueryClient;
import com.example.integration.infrastructure.core.common.gateway.BaseTradeGateway;
import com.example.integration.infrastructure.core.trade.dto.Submit5565Req;
import com.example.integration.infrastructure.core.trade.dto.Submit5565Res;
import com.example.integration.infrastructure.core.trade.mapper.FileSubmissionInfraMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * 文件提交网关实现
 * <p>
 * 流程：构造 5565 请求报文 → 通过 BaseTradeGateway 调用外部交易系统 → 转换为领域结果。
 * <p>
 * 当前实现采用 Strategy A（文件级提交）：
 * fileId 作为数据凭证传入，由外部交易系统通过 fileId 拉取实际文件内容。
 * 如果未来需要 Strategy B（数据级提交），应新建另一个 Gateway 实现，而非修改此类。
 */
@Slf4j
@Component
public class FileSubmissionGatewayImpl extends BaseTradeGateway implements FileSubmissionGateway {

  private final FileSubmissionInfraMapper fileSubmissionInfraMapper;

  public FileSubmissionGatewayImpl(ObjectMapper objectMapper,
                                   TradeQueryClient tradeQueryClient,
                                   FileSubmissionInfraMapper fileSubmissionInfraMapper) {
    super(objectMapper, tradeQueryClient);
    this.fileSubmissionInfraMapper = fileSubmissionInfraMapper;
  }

  @Override
  public FileSubmissionResult submit(String fileId, String tradeCode, Map<String, String> headParams) {
    log.info("提交出站文件至外部交易系统, fileId={}, tradeCode={}", fileId, tradeCode);

    // 1. 构造请求报文
    Submit5565Req reqBody = new Submit5565Req(fileId, null, null, null);

    // 2. 构造 Head 自定义器
    UnaryOperator<com.example.integration.infrastructure.core.common.model.TradeReqHead> headCustomizer =
      head -> customizeHead(head, headParams);

    // 3. 调用外部交易系统
    Submit5565Res res = performTradeCall(reqBody, headCustomizer, Submit5565Res.class);

    // 4. 转换为领域模型
    FileSubmissionResult result = fileSubmissionInfraMapper.toResult(res);
    log.info("出站文件提交完成, fileId={}, status={}", fileId, result.status());
    return result;
  }

  /**
   * 根据 headParams 自定义交易头信息
   */
  private com.example.integration.infrastructure.core.common.model.TradeReqHead customizeHead(
    com.example.integration.infrastructure.core.common.model.TradeReqHead head,
    Map<String, String> headParams) {

    if (headParams == null || headParams.isEmpty()) {
      return head;
    }

    var customized = head;
    if (headParams.containsKey("tellerNo")) {
      customized = customized.withTeller(headParams.get("tellerNo"), headParams.getOrDefault("tellerName", ""));
    }
    if (headParams.containsKey("channel")) {
      customized = customized.withChannel(headParams.get("channel"));
    }
    return customized;
  }
}
