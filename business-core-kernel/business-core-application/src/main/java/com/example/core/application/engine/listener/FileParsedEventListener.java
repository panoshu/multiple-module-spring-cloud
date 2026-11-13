package com.example.core.application.engine.listener;

import com.example.core.application.engine.callback.CallbackCommand;
import com.example.core.application.engine.callback.CallbackCorrelationService;
import com.example.core.application.engine.callback.CallbackOutcome;
import com.example.core.application.engine.callback.CorrelationResult;
import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.gateway.EventReceiptGateway;
import com.example.file.api.event.FileParsedEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 文件解析完成集成事件监听器。
 * <p>
 * 消费 {@link FileParsedEventDTO}（由 file-service 解析完成后发布），
 * 根据解析状态推进或终止业务申请单流程。
 * <p>
 * <b>【演示环境】</b>通过 Spring {@link EventListener} 接收本地 {@code ApplicationEventPublisher}
 * 发布的事件；<b>【生产环境】</b>由 RocketMQ 消费者直接调用 {@link #handleFileParsed(FileParsedEventDTO)}。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/7/14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileParsedEventListener {

  private static final String STATUS_SUCCESS = "SUCCESS";
  private static final String STATUS_PARTIAL = "PARTIAL";
  private static final String CONSUMER = "FileParsedEventListener";

  private final FlowOrchestrationService orchestrationService;
  private final EventReceiptGateway eventReceiptGateway;
  private final CallbackCorrelationService callbackCorrelationService;

  /**
   * Spring 事件入口：接收 {@link FileParsedEventDTO} 并委托给处理方法。
   *
   * @param event 文件解析完成事件 DTO
   */
  @EventListener
  public void onFileParsed(FileParsedEventDTO event) {
    handleFileParsed(event);
  }

  /**
   * 核心处理逻辑（Spring 和 RocketMQ 共用入口）。
   * <p>
   * 解析状态为 {@code SUCCESS} 或 {@code PARTIAL} 时推进业务流程；
   * 其他状态（如 {@code FAILED}）仅记录日志，等待人工干预或重试。
   *
   * @param event 文件解析完成事件 DTO
   */
  public void handleFileParsed(FileParsedEventDTO event) {
    if (!eventReceiptGateway.tryConsume(event.eventId(), CONSUMER)) {
      log.info("重复文件解析事件已忽略, eventId: {}", event.eventId());
      return;
    }
    log.info("收到文件解析完成事件, fileTaskId: {}, status: {}", event.fileTaskId(), event.status());
    boolean success = STATUS_SUCCESS.equals(event.status()) || STATUS_PARTIAL.equals(event.status());

    // 阶段 C：优先按 requestNo 关联外部协作并闭环状态（spec §9.2 / §11.4）
    CorrelationResult result = callbackCorrelationService.correlate(new CallbackCommand(
      event.requestNo(), event.fileTaskId(), InteractionType.FILE_PARSE,
      success ? CallbackOutcome.SUCCEEDED : CallbackOutcome.FAILED,
      event.failureReason(), "SYSTEM"));

    switch (result.classification()) {
      case APPLIED -> {
        if (success) {
          // 用关联到的权威 appId 推进，不再依赖 fileTaskId 反查
          orchestrationService.advanceStep(result.applicationId());
        } else {
          log.error("文件解析失败（协作已闭环为 FAILED，不推进）, fileTaskId: {}, failureReason: {}",
            event.fileTaskId(), event.failureReason());
        }
      }
      case NO_CORRELATION -> legacyAdvance(event, success); // 过渡期旧构造（无 requestNo）
      case UNMATCHED, OUT_OF_ORDER -> log.warn(
        "文件解析回调未推进流程, classification={}, requestNo={}, fileTaskId={}",
        result.classification(), event.requestNo(), event.fileTaskId());
    }
  }

  private void legacyAdvance(FileParsedEventDTO event, boolean success) {
    if (success) {
      orchestrationService.advanceByFileTaskId(event.fileTaskId());
    } else {
      log.error("文件解析失败, fileTaskId: {}, failureReason: {}",
        event.fileTaskId(), event.failureReason());
    }
  }
}
