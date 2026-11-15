package com.example.core.adapter.inbound;

import com.example.approval.api.event.ApprovalInstanceApprovedEventDTO;
import com.example.approval.api.event.ApprovalInstanceRejectedEventDTO;
import com.example.approval.api.event.ApprovalInstanceWithdrawnEventDTO;
import com.example.core.application.engine.inbound.ApprovalResultMessage;
import com.example.core.application.engine.inbound.ApprovalResultMessage.ApprovalResult;
import com.example.core.application.engine.inbound.FileParsedMessage;
import com.example.core.application.engine.inbound.OutboundFileGeneratedMessage;
import com.example.file.api.event.FileParsedEventDTO;
import com.example.file.api.event.OutboundFileGeneratedEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 外部集成事件入站适配器。
 * <p>
 * 把 file-service / approval-service 的 API 事件 DTO 转换为 kernel 自有入站消息，
 * 再交给应用层监听器消费。应用层因此不直接依赖外部服务的 API 模块，
 * kernel 可以独立复用并替换外部实现；生产环境接入 MQ 时，也只需新增消费者
 * 调用同样的映射方法。
 *
 * @author business-core-kernel
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalEventInboundAdapter {

  private final ApplicationEventPublisher publisher;

  /**
   * file-service 文件解析完成事件 → kernel 文件解析消息。
   */
  @EventListener
  public void onFileParsed(FileParsedEventDTO event) {
    List<FileParsedMessage.ParsedSubTaskMessage> subTasks = event.subTasks() == null
      ? List.of()
      : event.subTasks().stream()
        .map(subTask -> new FileParsedMessage.ParsedSubTaskMessage(
          subTask.subTaskId(),
          subTask.splitKey(),
          subTask.totalRows(),
          subTask.validRows(),
          subTask.invalidRows(),
          subTask.status()))
        .toList();
    publisher.publishEvent(new FileParsedMessage(
      event.eventId(),
      event.fileTaskId(),
      event.requestNo(),
      event.status(),
      event.totalSubTasks(),
      subTasks,
      event.failureReason()));
  }

  /**
   * file-service 出站文件生成事件 → kernel 出站生成消息。
   */
  @EventListener
  public void onOutboundFileGenerated(OutboundFileGeneratedEventDTO event) {
    publisher.publishEvent(new OutboundFileGeneratedMessage(
      event.eventId(),
      event.fileTaskId(),
      event.requestNo(),
      event.targetTemplateId(),
      event.generatedFiles() == null ? 0 : event.generatedFiles().size()));
  }

  /**
   * approval-service 审批通过事件 → kernel 审批结果消息。
   */
  @EventListener
  public void onApproved(ApprovalInstanceApprovedEventDTO event) {
    publishApproval(event.eventId(), event.instanceId(), event.businessNo(),
      event.requestNo(), ApprovalResult.APPROVED);
  }

  /**
   * approval-service 审批驳回事件 → kernel 审批结果消息。
   */
  @EventListener
  public void onRejected(ApprovalInstanceRejectedEventDTO event) {
    publishApproval(event.eventId(), event.instanceId(), event.businessNo(),
      event.requestNo(), ApprovalResult.REJECTED);
  }

  /**
   * approval-service 审批撤回事件 → kernel 审批结果消息。
   */
  @EventListener
  public void onWithdrawn(ApprovalInstanceWithdrawnEventDTO event) {
    publishApproval(event.eventId(), event.instanceId(), event.businessNo(),
      event.requestNo(), ApprovalResult.WITHDRAWN);
  }

  private void publishApproval(String eventId, String instanceId, String businessNo,
                               String requestNo, ApprovalResult result) {
    publisher.publishEvent(new ApprovalResultMessage(
      eventId, instanceId, businessNo, requestNo, result));
  }
}
