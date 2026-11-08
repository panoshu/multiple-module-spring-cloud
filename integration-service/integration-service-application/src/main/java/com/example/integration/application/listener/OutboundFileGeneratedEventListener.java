package com.example.integration.application.listener;

import com.example.file.api.event.OutboundFileGeneratedEventDTO;
import com.example.integration.application.trade.service.TradeSubmissionAppService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 出站文件已生成事件监听器（2026-11-08 批次4 C2：导出闭环——设计决策 D1 三方分工）.
 *
 * <p><b>链路</b>：业务服务触发导出（kernel）→ file-service 模板填充生成 →
 * OutboundFileGeneratedEvent（Redis 事件通道）→ 本监听器 →
 * {@link TradeSubmissionAppService#submitOutboundFile} 逐文件流式拉取并
 * 协议适配提交外部系统.</p>
 *
 * <p><b>职责边界</b>：本服务只做"拉取+协议适配+提交"（file 域外文件字节流的
 * 唯一合法流转方）；业务语义（tradeCode/headParams 的业务映射）由配置或
 * 事件演进字段提供（批次5 事件载荷扩展）.</p>
 *
 * @author integration-service
 */
@Slf4j
@Component
public class OutboundFileGeneratedEventListener {

  private final TradeSubmissionAppService tradeSubmission;

  @Value("${integration.outbound.trade-code:5564}")
  private String defaultTradeCode;

  public OutboundFileGeneratedEventListener(TradeSubmissionAppService tradeSubmission) {
    this.tradeSubmission = tradeSubmission;
  }

  @EventListener
  public void onOutboundFileGenerated(OutboundFileGeneratedEventDTO event) {
    log.info("收到出站文件生成事件: fileTaskId={}, targetTemplateId={}, files={}",
      event.fileTaskId(), event.targetTemplateId(),
      event.generatedFiles() == null ? 0 : event.generatedFiles().size());
    if (event.generatedFiles() == null || event.generatedFiles().isEmpty()) {
      log.warn("事件无生成文件清单，跳过: fileTaskId={}", event.fileTaskId());
      return;
    }
    for (OutboundFileGeneratedEventDTO.GeneratedFileInfoDTO file : event.generatedFiles()) {
      try {
        var result = tradeSubmission.submitOutboundFile(
          file.outboundFileId(), defaultTradeCode, java.util.Map.of());
        log.info("出站文件已提交外部系统: fileId={}, subTaskId={}, splitKey={}, result={}",
          file.outboundFileId(), file.subTaskId(), file.splitKeyValue(), result);
      } catch (Exception e) {
        // 单文件失败隔离：记录后继续其余文件（批量提交的部分成功语义）
        log.error("出站文件提交失败（隔离继续）: fileId={}, subTaskId={}",
          file.outboundFileId(), file.subTaskId(), e);
      }
    }
  }
}
