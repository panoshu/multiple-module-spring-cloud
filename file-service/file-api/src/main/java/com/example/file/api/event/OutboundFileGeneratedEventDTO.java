package com.example.file.api.event;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 出站文件生成完成集成事件 DTO
 * <p>
 * 对应 file-domain 的 OutboundFileGeneratedEvent 领域事件。
 * 由 kernel 的 OutboundFileEventListener 消费，推进 BusinessApplication 步骤。
 */
public record OutboundFileGeneratedEventDTO(
  String eventId,
  String fileTaskId,
  String targetTemplateId,
  List<GeneratedFileInfoDTO> generatedFiles,
  LocalDateTime occurredOn
) {
  public record GeneratedFileInfoDTO(
    String subTaskId,
    String splitKeyValue,
    String outboundFileId
  ) {
  }
}
