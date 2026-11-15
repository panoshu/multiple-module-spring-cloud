package com.example.shared.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S-01：EventId 从 shared-types 迁入 shared-domain 后的契约测试。
 */
@DisplayName("EventId 事件标识契约")
class EventIdTest {

  @Test
  @DisplayName("null/blank 应拒绝")
  void rejectsNullOrBlank() {
    assertThatThrownBy(() -> new EventId(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new EventId(" ")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("generate() 返回 26 位 ULID 且高并发不重复")
  void generateProducesUniqueUlid() {
    Set<String> values = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      EventId id = EventId.generate();
      assertThat(id.value()).hasSize(26);
      assertThat(values.add(id.value())).isTrue();
    }
  }
}
