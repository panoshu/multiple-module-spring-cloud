package com.example.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成事件消费入口契约（批次 45，P0-1 / P0-3）。
 * <p>
 * 防止「同一个集成事件 DTO 被多个 {@code @EventListener} 同时消费」——重复消费会绕过
 * 消费回执与回调关联，导致重复跃迁。同时要求所有集成事件监听入口所在类持有
 * {@code EventReceiptGateway}，保证按 eventId 幂等不因新增监听器而遗漏。
 */
@DisplayName("集成事件监听入口唯一性与幂等契约（批次 45）")
class EventListenerUniquenessTest {

  private static final String LISTENER_PACKAGE = "com.example.core.application.engine.listener";
  private static final String INBOUND_ADAPTER_PACKAGE = "com.example.core.adapter.inbound";
  private static final String KERNEL_INBOUND_MESSAGE = "com.example.core.application.engine.inbound.";
  private static final String EVENT_LISTENER = "org.springframework.context.event.EventListener";
  private static final String OUTBOUND_DTO = "com.example.file.api.event.OutboundFileGeneratedEventDTO";
  private static final String RECEIPT_GATEWAY = "com.example.core.domain.engine.gateway.EventReceiptGateway";

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes = new ClassFileImporter().importPackages(
      "com.example.core", "com.example.file", "com.example.approval");
    assertThat(classes.size()).isGreaterThan(500);
  }

  @Test
  @DisplayName("OutboundFileGeneratedEventDTO 只能有一个入站适配器入口")
  void outboundGeneratedEvent_hasSingleListener() {
    long listenerCount = 0;
    for (JavaClass clazz : classes) {
      if (!clazz.getPackageName().startsWith(INBOUND_ADAPTER_PACKAGE)) {
        continue;
      }
      for (JavaMethod method : clazz.getMethods()) {
        if (method.isAnnotatedWith(EVENT_LISTENER) && handles(method, OUTBOUND_DTO)) {
          listenerCount++;
        }
      }
    }

    assertThat(listenerCount)
      .as("同一集成事件被多个监听器消费会绕过回执与关联，造成重复跃迁")
      .isEqualTo(1L);
  }

  @Test
  @DisplayName("kernel 入站消息监听器持有 EventReceiptGateway；外部 DTO 只由 adapter 映射")
  void everyIntegrationEventListener_hasReceiptGateway() {
    int checked = 0;
    for (JavaClass clazz : classes) {
      if (!clazz.getPackageName().startsWith(LISTENER_PACKAGE)) {
        continue;
      }
      boolean handlesKernelMessage = clazz.getMethods().stream()
        .anyMatch(method -> method.isAnnotatedWith(EVENT_LISTENER)
          && method.getRawParameterTypes().stream()
          .anyMatch(param -> param.getName().startsWith(KERNEL_INBOUND_MESSAGE)));
      if (!handlesKernelMessage) {
        continue;
      }
      checked++;
      boolean hasReceipt = clazz.getFields().stream()
        .map(JavaField::getRawType)
        .anyMatch(type -> type.getName().equals(RECEIPT_GATEWAY));
      assertThat(hasReceipt)
        .as("%s 消费 kernel 入站消息但缺少按 eventId 幂等的回执网关", clazz.getName())
        .isTrue();
    }

    assertThat(checked).isGreaterThan(0);

    // K-07：application 不得直接监听外部服务事件 DTO
    for (JavaClass clazz : classes) {
      if (!clazz.getPackageName().startsWith(LISTENER_PACKAGE)) {
        continue;
      }
      for (JavaMethod method : clazz.getMethods()) {
        if (!method.isAnnotatedWith(EVENT_LISTENER)) {
          continue;
        }
        assertThat(method.getRawParameterTypes())
          .as("%s#%s 不得直接消费外部事件 DTO", clazz.getSimpleName(), method.getName())
          .noneMatch(param -> param.getPackageName().contains(".api.event"));
      }
    }
  }

  private boolean handles(JavaMethod method, String dtoName) {
    return method.getRawParameterTypes().stream()
      .anyMatch(param -> param.getName().equals(dtoName));
  }
}
