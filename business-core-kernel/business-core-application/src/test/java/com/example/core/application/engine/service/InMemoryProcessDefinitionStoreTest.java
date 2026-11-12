package com.example.core.application.engine.service;

import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.ProcessDefinition;
import com.example.shared.exception.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.example.core.application.engine.service.ProcessDefinitionFixtures.TYPE;
import static com.example.core.application.engine.service.ProcessDefinitionFixtures.definition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InMemoryProcessDefinitionStore 版本化定义存储")
class InMemoryProcessDefinitionStoreTest {

  private InMemoryProcessDefinitionStore store;

  @BeforeEach
  void setUp() {
    store = new InMemoryProcessDefinitionStore();
  }

  @Test
  @DisplayName("按业务类型与版本保存已发布定义")
  void publish_storesByTypeAndVersion() {
    ProcessDefinition v1 = definition("v1");

    store.publish(v1);

    assertThat(store.load(TYPE, DefinitionVersion.of("v1"))).contains(v1);
  }

  @Test
  @DisplayName("重复发布同一版本被拒绝，已发布定义不可变")
  void publish_rejectsDuplicateVersion() {
    store.publish(definition("v1"));

    assertThatThrownBy(() -> store.publish(definition("v1")))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("历史版本仍可按版本读取")
  void load_keepsHistoryVersions() {
    ProcessDefinition v1 = definition("v1");
    ProcessDefinition v2 = definition("v2");
    store.publish(v1);
    store.publish(v2);

    assertThat(store.load(TYPE, DefinitionVersion.of("v1"))).contains(v1);
    assertThat(store.load(TYPE, DefinitionVersion.of("v2"))).contains(v2);
  }

  @Test
  @DisplayName("loadLatest 随新版本发布更新（缓存失效）")
  void loadLatest_tracksLastPublished() {
    store.publish(definition("v1"));
    assertThat(store.loadLatest(TYPE)).contains(definition("v1"));

    ProcessDefinition v2 = definition("v2");
    store.publish(v2);
    assertThat(store.loadLatest(TYPE)).contains(v2);
  }

  @Test
  @DisplayName("未发布任何定义时查询返回空")
  void emptyStore_returnsEmpty() {
    assertThat(store.loadLatest(TYPE)).isEmpty();
    assertThat(store.load(TYPE, DefinitionVersion.initial())).isEmpty();
  }
}
