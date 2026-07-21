package com.example.core.application.listener;

import com.example.core.application.service.BusinessOrchestrationAppService;
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

  private final BusinessOrchestrationAppService orchestrationService;

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
    log.info("收到文件解析完成事件, fileTaskId: {}, status: {}", event.fileTaskId(), event.status());
    if (STATUS_SUCCESS.equals(event.status()) || STATUS_PARTIAL.equals(event.status())) {
      orchestrationService.advanceByFileTaskId(event.fileTaskId());
    } else {
      log.error("文件解析失败, fileTaskId: {}, failureReason: {}",
        event.fileTaskId(), event.failureReason());
    }
  }
}
