package com.example.core.application.engine.service;

import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.core.domain.engine.service.registry.StepActionHandlerRegistry;
import com.example.shared.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.example.core.application.engine.service.ProcessDefinitionFixtures.HANDLER;
import static com.example.core.application.engine.service.ProcessDefinitionFixtures.TYPE;
import static com.example.core.application.engine.service.ProcessDefinitionFixtures.definition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProcessDefinitionPublishService 定义发布校验")
class ProcessDefinitionPublishServiceTest {

  @Mock
  private StepActionHandlerRegistry handlerRegistry;

  private InMemoryProcessDefinitionStore store;
  private ProcessDefinitionPublishService service;

  @BeforeEach
  void setUp() {
    store = new InMemoryProcessDefinitionStore();
    service = new ProcessDefinitionPublishService(store, handlerRegistry);
  }

  @Test
  @DisplayName("Handler 齐备时发布后可按最新版本读取")
  void publish_persistsWhenHandlersExist() {
    when(handlerRegistry.contains(HANDLER)).thenReturn(true);
    ProcessDefinition def = definition("v1");

    service.publish(def);

    assertThat(store.loadLatest(TYPE)).contains(def);
  }

  @Test
  @DisplayName("主处理器未注册时拒绝发布且不落库")
  void publish_rejectsWhenHandlerMissing() {
    when(handlerRegistry.contains(HANDLER)).thenReturn(false);

    assertThatThrownBy(() -> service.publish(definition("v1")))
      .isInstanceOf(DomainException.class);
    assertThat(store.loadLatest(TYPE)).isEmpty();
  }

  @Test
  @DisplayName("重复版本发布被拒绝")
  void publish_rejectsDuplicateVersion() {
    when(handlerRegistry.contains(HANDLER)).thenReturn(true);
    service.publish(definition("v1"));

    assertThatThrownBy(() -> service.publish(definition("v1")))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("空定义被拒绝")
  void publish_rejectsNullDefinition() {
    assertThatThrownBy(() -> service.publish(null))
      .isInstanceOf(DomainException.class);
  }
}
