package com.example.iam.application.event.converter;

import com.example.iam.api.event.IntegrationEventTypes;
import com.example.iam.api.event.SecondaryAuthCompletedEventDTO;
import com.example.iam.domain.authentication.event.SecondaryAuthCompletedEvent;
import com.example.shared.domain.event.IntegrationEventConverter;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 二次授权完成领域事件 -> 集成事件转换器。
 *
 * <p><b>TODO:</b> {@link SecondaryAuthCompletedEvent} 当前未携带 {@code customerNo} 与
 * {@code permissionSnapshot} 字段,此处暂以 {@code null} 与空集合填充。
 * 待领域事件补齐字段后,需将 {@code PermissionSnapshot} 中的 {@code PermissionCode} 集合
 * 转换为 {@code Set<String>}。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Component
public class SecondaryAuthCompletedEventConverter
    implements IntegrationEventConverter<SecondaryAuthCompletedEvent> {

  @Override
  public Class<SecondaryAuthCompletedEvent> supportedEventType() {
    return SecondaryAuthCompletedEvent.class;
  }

  @Override
  public Object toIntegrationEvent(SecondaryAuthCompletedEvent event) {
    // TODO: 领域事件补齐 customerNo 与 permissionSnapshot 字段后,此处需同步映射
    return new SecondaryAuthCompletedEventDTO(
        event.eventId().value(),
        event.sessionId().value(),
        event.tellerId(),
        event.approverId(),
        event.planId(),
        null,
        Set.of(),
        event.authorizedAt(),
        event.expireAt(),
        event.occurredOn()
    );
  }

  @Override
  public String integrationEventType() {
    return IntegrationEventTypes.SECONDARY_AUTH_COMPLETED;
  }
}
