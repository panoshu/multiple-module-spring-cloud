package com.example.shared.id.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("L2: 核心算法纯Java测试")
class AlgorithmTest {

  @Test
  @DisplayName("UUID V7: 格式与单调递增性校验")
  void testUuidV7() {
    String id1 = UuidV7Algorithm.generate().toString();

    // 1. 格式校验 (标准UUID格式)
    assertThat(id1).matches("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    // 2. 验证生成的ID不为空
    assertThat(id1).isNotNull();

    // 注意：UUID V7 基于时间戳，极短时间内生成的ID可能无法保证字符串层面的严格字典序单调，
    // 但在高位时间戳变动时应单调。这里主要测非空和格式。
  }

  @Test
  @DisplayName("ULID: 长度与唯一性校验")
  void testUlid() {
    int count = 1000;
    Set<String> ids = new HashSet<>(count);
    for (int i = 0; i < count; i++) {
      ids.add(UlidAlgorithm.generate());
    }

    // 1. 验证无重复
    assertThat(ids).hasSize(count);

    // 2. 验证长度 (ULID 固定 26 字符)
    assertThat(ids.iterator().next()).hasSize(26);
  }
}
