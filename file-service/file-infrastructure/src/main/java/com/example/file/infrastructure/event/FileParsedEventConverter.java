package com.example.file.infrastructure.event;

import com.example.file.api.event.FileParsedEventDTO;
import com.example.file.domain.event.FileParsedEvent;
import com.example.shared.event.contract.IntegrationEventConverter;
import org.springframework.stereotype.Component;

/**
 * 文件解析完成领域事件 → 集成事件转换器（2026-11-08 批次1 接线）.
 *
 * <p>FileParsedEvent 同时承载成功/失败态（status + failureReason）——
 * 消费方（kernel 的 FileParsedEventListener）按 status 分支推进。</p>
 *
 * @author file-service
 */
@Component
public class FileParsedEventConverter implements IntegrationEventConverter<FileParsedEvent> {

  @Override
  public Class<FileParsedEvent> supportedEventType() {
    return FileParsedEvent.class;
  }

  @Override
  public Object toIntegrationEvent(FileParsedEvent event) {
    return new FileParsedEventDTO(
      event.eventId().value(),
      event.fileTaskId().value(),
      event.bizType().value(),
      event.status().name(),
      event.totalSubTasks(),
      event.subTasks().stream()
        .map(s -> new FileParsedEventDTO.SubTaskSummaryDTO(
          s.subTaskId().value(),
          s.splitKeyValue(),
          s.totalRows(),
          s.validRows(),
          s.invalidRows(),
          s.status().name()))
        .toList(),
      event.failureReason(),
      event.occurredOn()
    );
  }
}
