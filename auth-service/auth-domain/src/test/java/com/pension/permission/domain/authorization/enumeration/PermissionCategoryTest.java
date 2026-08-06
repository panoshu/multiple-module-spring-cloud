package com.pension.permission.domain.authorization.enumeration;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PermissionCategoryTest {

  @Test
  void should_have_business_and_platform_values() {
    assertThat(PermissionCategory.values())
      .containsExactlyInAnyOrder(PermissionCategory.BUSINESS, PermissionCategory.PLATFORM);
  }

  @Test
  void business_depends_on_plan() {
    assertThat(PermissionCategory.BUSINESS.requiresPlan()).isTrue();
  }

  @Test
  void platform_does_not_depend_on_plan() {
    assertThat(PermissionCategory.PLATFORM.requiresPlan()).isFalse();
  }
}
