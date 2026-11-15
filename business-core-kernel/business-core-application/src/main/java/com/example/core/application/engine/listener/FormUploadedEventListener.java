package com.example.core.application.engine.listener;

import com.example.core.application.engine.step.service.BusinessFormAppService;
import com.example.core.domain.business.event.FormUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 表单上传完成事件监听器（三阶段流程 Phase 1 → Phase 2）.
 *
 * <p>在 {@code confirmUpload} 事务提交后触发文件解析。此前该监听器缺失，
 * 导致 {@code FormUploadedEvent} 发布后无人消费、{@code triggerParsing} 成为
 * 无调用者的死代码，表单永远不会进入异步解析阶段。</p>
 *
 * <p>使用 {@code AFTER_COMMIT} 保证表单及上传文件已经落库；失败只记录日志，
 * 不反向影响已提交的上传事务，后续可由补偿任务或人工重试。</p>
 *
 * @author business-core-kernel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormUploadedEventListener {

  private final BusinessFormAppService formAppService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onFormUploaded(FormUploadedEvent event) {
    log.info("收到表单上传完成事件，触发异步解析: formId={}, fileId={}",
      event.formId(), event.fileId());
    try {
      formAppService.triggerParsing(event.formId());
    } catch (Exception e) {
      log.error("触发表单异步解析失败，等待补偿或人工重试: formId={}", event.formId(), e);
    }
  }
}
