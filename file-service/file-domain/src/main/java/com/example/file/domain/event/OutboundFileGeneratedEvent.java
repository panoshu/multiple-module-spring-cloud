package com.example.file.domain.event;

import com.example.file.types.FileTaskId;
import com.example.shared.domain.event.DomainEvent;
import com.example.shared.identifier.id.EventId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 出站文件生成完成领域事件。
 * <p>
 * 当 file-service 完成所有 SubTaskData 的出站 Excel 生成后发布此事件，
 * 由 kernel 的 {@code OutboundFileGeneratedEventListener} 消费，推进 BusinessApplication 步骤。
 * <p>
 * 出站粒度为 1:N（每个 SubTaskData 生成一个文件），本事件聚合所有结果。
 */
public record OutboundFileGeneratedEvent(
  EventId eventId,
  LocalDateTime occurredOn,
  FileTaskId fileTaskId,
  List<GeneratedFileInfo> generatedFiles,
  String targetTemplateId,
  String requestNo
) implements DomainEvent {

  public static OutboundFileGeneratedEvent of(FileTaskId fileTaskId,
                                               List<GeneratedFileInfo> generatedFiles,
                                               String targetTemplateId) {
    return of(fileTaskId, generatedFiles, targetTemplateId, null);
  }

  public static OutboundFileGeneratedEvent of(FileTaskId fileTaskId,
                                               List<GeneratedFileInfo> generatedFiles,
                                               String targetTemplateId, String requestNo) {
    return new OutboundFileGeneratedEvent(
      EventId.generate(),
      LocalDateTime.now(),
      fileTaskId,
      generatedFiles,
      targetTemplateId,
      requestNo
    );
  }

  /**
   * 单个出站文件的描述信息。
   */
  public record GeneratedFileInfo(
    String subTaskId,
    String splitKeyValue,
    String outboundFileId
  ) {}
}
