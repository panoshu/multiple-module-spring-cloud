package com.pension.permission.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("测试环境冒烟测试")
class SmokeTest {

  @Test
  @DisplayName("JUnit5 和 AssertJ 应当可用")
  void should_work_when_junit5_and_assertj_available() {
    assertThat(1 + 1).isEqualTo(2);
  }
}
