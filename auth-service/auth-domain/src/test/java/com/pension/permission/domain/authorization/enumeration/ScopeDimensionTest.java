package com.pension.permission.domain.authorization.enumeration;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScopeDimensionTest {

  @Test
  void should_contain_global_value() {
    assertThat(ScopeDimension.values())
      .contains(ScopeDimension.GLOBAL);
  }

  @Test
  void should_have_five_dimensions_plus_global() {
    assertThat(ScopeDimension.values()).hasSize(6);
  }
}
