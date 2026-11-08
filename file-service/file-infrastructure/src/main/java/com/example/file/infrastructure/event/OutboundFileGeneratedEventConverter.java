package com.example.file.infrastructure.event;

import com.example.file.api.event.OutboundFileGeneratedEventDTO;
import com.example.file.domain.event.OutboundFileGeneratedEvent;
import com.example.shared.event.contract.IntegrationEventConverter;
import org.springframework.stereotype.Component;

/**
 * 出站文件已生成领域事件 → 集成事件转换器（2026-11-08 批次4 C1：导出闭环接线）.
 *
 * <p>消费方（integration-service）：监听后流式拉取生成文件做协议适配提交外部系统
 * （设计决策 D1 三方分工——file 生成 / integration 提交 / 业务触发）.</p>
 *
 * @author file-service
 */
@Component
public class OutboundFileGeneratedEventConverter
  implements IntegrationEventConverter<OutboundFileGeneratedEvent> {

  @Override
  public Class<OutboundFileGeneratedEvent> supportedEventType() {
    return OutboundFileGeneratedEvent.class;
  }

  @Override
  public Object toIntegrationEvent(OutboundFileGeneratedEvent event) {
    return new OutboundFileGeneratedEventDTO(
      event.eventId().value(),
      event.fileTaskId().value(),
      event.targetTemplateId(),
      event.generatedFiles().stream()
        .map(f -> new OutboundFileGeneratedEventDTO.GeneratedFileInfoDTO(
          f.subTaskId(),
          f.splitKeyValue(),
          f.outboundFileId()))
        .toList(),
      event.occurredOn()
    );
  }
}
