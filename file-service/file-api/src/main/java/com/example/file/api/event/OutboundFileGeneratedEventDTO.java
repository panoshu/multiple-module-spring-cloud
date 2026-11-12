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
  LocalDateTime occurredOn,
  /** 外部协作幂等键；过渡期可空，阶段 3 回调按此关联协作记录。 */
  String requestNo
) {

  public OutboundFileGeneratedEventDTO(String eventId, String fileTaskId, String targetTemplateId,
                                       List<GeneratedFileInfoDTO> generatedFiles,
                                       LocalDateTime occurredOn) {
    this(eventId, fileTaskId, targetTemplateId, generatedFiles, occurredOn, null);
  }

  public record GeneratedFileInfoDTO(
    String subTaskId,
    String splitKeyValue,
    String outboundFileId
  ) {
  }
}
