package com.example.core.application.engine.service;

import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.core.domain.engine.gateway.ProcessDefinitionGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.example.core.application.engine.service.ProcessDefinitionFixtures.TYPE;
import static com.example.core.application.engine.service.ProcessDefinitionFixtures.definition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompositeProcessDefinitionGateway 组合式定义加载")
class CompositeProcessDefinitionGatewayTest {

  @Mock
  private ProcessDefinitionGateway fallback;

  private InMemoryProcessDefinitionStore store;
  private CompositeProcessDefinitionGateway gateway;

  @BeforeEach
  void setUp() {
    store = new InMemoryProcessDefinitionStore();
    gateway = new CompositeProcessDefinitionGateway(store, fallback);
  }

  @Test
  @DisplayName("存储命中时优先返回且不触发存量路由")
  void load_prefersStoredDefinition() {
    ProcessDefinition def = definition("v1");
    store.publish(def);

    assertThat(gateway.load(TYPE, DefinitionVersion.of("v1"))).contains(def);
    verifyNoInteractions(fallback);
  }

  @Test
  @DisplayName("存储未命中时回退存量路由适配")
  void load_fallsBackWhenNotStored() {
    ProcessDefinition fallbackDef = definition("v1");
    when(fallback.load(TYPE, DefinitionVersion.of("v1"))).thenReturn(Optional.of(fallbackDef));

    assertThat(gateway.load(TYPE, DefinitionVersion.of("v1"))).contains(fallbackDef);
  }

  @Test
  @DisplayName("非初始版本可从存储读取历史定义")
  void load_readsHistoricalVersionFromStore() {
    ProcessDefinition v1 = definition("v1");
    ProcessDefinition v2 = definition("v2");
    store.publish(v1);
    store.publish(v2);

    assertThat(gateway.load(TYPE, DefinitionVersion.of("v1"))).contains(v1);
    assertThat(gateway.loadLatest(TYPE)).contains(v2);
  }

  @Test
  @DisplayName("存储为空时 loadLatest 回退存量路由")
  void loadLatest_fallsBackWhenStoreEmpty() {
    ProcessDefinition fallbackDef = definition("v1");
    when(fallback.loadLatest(TYPE)).thenReturn(Optional.of(fallbackDef));

    assertThat(gateway.loadLatest(TYPE)).contains(fallbackDef);
  }
}
