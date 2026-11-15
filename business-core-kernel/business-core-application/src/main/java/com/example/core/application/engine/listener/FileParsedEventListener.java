package com.example.core.application.engine.listener;

import com.example.core.application.engine.callback.CallbackCommand;
import com.example.core.application.engine.callback.CallbackCorrelationService;
import com.example.core.application.engine.callback.CallbackOutcome;
import com.example.core.application.engine.callback.CorrelationResult;
import com.example.core.application.engine.inbound.FileParsedMessage;
import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.application.engine.step.service.BusinessFormAppService;
import com.example.core.domain.business.aggregate.valueobject.ParsedPlanResult;
import com.example.core.domain.business.repository.FormRepository;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.gateway.EventReceiptGateway;
import com.example.core.types.FormId;
import com.example.core.types.PlanNo;
import com.example.core.types.FileRefId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * 文件解析完成集成事件监听器。
 * <p>
 * 消费 {@link FileParsedMessage}（由 adapter 层把 file-service 集成事件转换后发布），
 * 根据解析状态推进或终止业务申请单流程。
 * <p>
 * <b>【演示环境】</b>通过 Spring {@link EventListener} 接收本地 {@code ApplicationEventPublisher}
 * 发布的事件；<b>【生产环境】</b>由 RocketMQ 消费者直接调用 {@link #handleFileParsed(FileParsedMessage)}。
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
  private final BusinessFormAppService formAppService;
  private final FormRepository formRepository;

  /**
   * Spring 事件入口：接收 {@link FileParsedMessage} 并委托给处理方法。
   *
   * @param event 文件解析完成入站消息
   */
  @EventListener
  public void onFileParsed(FileParsedMessage event) {
    handleFileParsed(event);
  }

  /**
   * 核心处理逻辑（Spring 和 RocketMQ 共用入口）。
   * <p>
   * 解析状态为 {@code SUCCESS} 或 {@code PARTIAL} 时推进业务流程；
   * 其他状态（如 {@code FAILED}）仅记录日志，等待人工干预或重试。
   * PARTIAL 时把失败明细统计（批次 64）写入 {@code resultRef}，随协作闭环持久化，
   * 供业务侧端到端对账，避免部分成功被折叠成「全部成功」。
   *
   * @param event 文件解析完成入站消息
   */
  public void handleFileParsed(FileParsedMessage event) {
    if (!eventReceiptGateway.tryConsume(event.eventId(), CONSUMER)) {
      log.info("重复文件解析事件已忽略, eventId: {}", event.eventId());
      return;
    }
    log.info("收到文件解析完成事件, fileTaskId: {}, status: {}", event.fileTaskId(), event.status());
    boolean partial = STATUS_PARTIAL.equals(event.status());
    boolean success = STATUS_SUCCESS.equals(event.status()) || partial;
    String resultRef = partial
      ? PartialParseStatistics.from(event).toResultRef()
      : event.failureReason();

    // 阶段 A/B：requestNo 命中表单 → 解析结果是「表单裂变」而非「申请单等待步骤回调」。
    // BusinessFormAppService.triggerParsing 以 formId 作为 requestNo 下发，file-service 原样回传。
    Optional<FormId> formId = resolveFormId(event);
    if (formId.isPresent()) {
      handleFormLevelResult(formId.get(), event, success);
      return;
    }

    // 阶段 C：优先按 requestNo 关联外部协作并闭环状态（spec §9.2 / §11.4）
    CorrelationResult result = callbackCorrelationService.correlate(new CallbackCommand(
      event.requestNo(), event.fileTaskId(), InteractionType.FILE_PARSE,
      success ? CallbackOutcome.SUCCEEDED : CallbackOutcome.FAILED,
      resultRef, "SYSTEM"));

    switch (result.classification()) {
      case APPLIED -> {
        if (success) {
          // 用关联到的权威 appId 完成等待步骤（不重跑 Handler 管线、不重复触发外部调用）
          orchestrationService.completeWaitingStep(result.applicationId());
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

  /**
   * 表单级解析结果：标记表单 PARSED，并按拆分出的计划裂变为 BusinessApplication。
   * 新申请单由 ApplicationSpawnedListener 自动点火推进，不再走申请单等待步骤回调。
   */
  private void handleFormLevelResult(FormId formId, FileParsedMessage event, boolean success) {
    if (!success) {
      log.error("表单级文件解析失败，等待人工/重试: formId={}, fileTaskId={}, reason={}",
        formId.value(), event.fileTaskId(), event.failureReason());
      return;
    }
    List<ParsedPlanResult> results = toParsedPlanResults(event);
    formAppService.handleParsingResult(formId, results);
    log.info("表单解析结果已裂变: formId={}, plans={}", formId.value(), results.size());
  }

  /**
   * requestNo 与 formId 同源（triggerParsing 下发 formId），命中则视为表单级解析事件。
   */
  private Optional<FormId> resolveFormId(FileParsedMessage event) {
    if (!StringUtils.hasText(event.requestNo())) {
      return Optional.empty();
    }
    FormId candidate = new FormId(event.requestNo());
    return formRepository.load(candidate).map(form -> form.id());
  }

  /**
   * file-service subTask → kernel 裂变结果。
   * splitKey 作为计划号；subTaskId 供后续 fetchParsedRows 分页拉取 canonical 行数据。
   */
  private List<ParsedPlanResult> toParsedPlanResults(FileParsedMessage event) {
    if (event.subTasks() == null || event.subTasks().isEmpty()) {
      return List.of();
    }
    return event.subTasks().stream()
      .filter(subTask -> StringUtils.hasText(subTask.subTaskId()))
      .filter(subTask -> StringUtils.hasText(subTask.splitKey()))
      .map(subTask -> new ParsedPlanResult(
        PlanNo.of(subTask.splitKey()),
        new FileRefId(subTask.subTaskId()),
        subTask.validRows() > 0 ? subTask.validRows() : subTask.totalRows(),
        subTask.subTaskId()))
      .toList();
  }

  private void legacyAdvance(FileParsedMessage event, boolean success) {
    if (success) {
      orchestrationService.advanceByFileTaskId(event.fileTaskId());
    } else {
      log.error("文件解析失败, fileTaskId: {}, failureReason: {}",
        event.fileTaskId(), event.failureReason());
    }
  }
}
