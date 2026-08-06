package com.pension.permission.domain.authorization.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Permission 值对象测试")
class PermissionTest {

  @Nested
  @DisplayName("构造校验")
  class ConstructionTest {

    @Test
    @DisplayName("businessCode 为 null 应抛 NullPointerException")
    void shouldThrowWhenBusinessCodeNull() {
      assertThatThrownBy(() -> new Permission(null, new ActionCode("ACT")))
        .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("wholeBusiness 应创建 actionCode 为 null 的权限")
    void shouldCreateWholeBusinessWithNullAction() {
      var perm = Permission.wholeBusiness(new BusinessCode("BIZ-001"));
      assertThat(perm.actionCode()).isNull();
    }
  }

  @Nested
  @DisplayName("covers 方法")
  class CoversTest {

    @Test
    @DisplayName("actionCode 为 null 时应覆盖任意操作")
    void shouldCoverAnyActionWhenActionCodeNull() {
      var perm = Permission.wholeBusiness(new BusinessCode("BIZ-001"));
      assertThat(perm.covers(new BusinessCode("BIZ-001"), new ActionCode("ANY"))).isTrue();
    }

    @Test
    @DisplayName("actionCode 非 null 时应精确匹配操作")
    void shouldMatchExactAction() {
      var perm = new Permission(new BusinessCode("BIZ-001"), new ActionCode("ACT-VIEW"));
      assertThat(perm.covers(new BusinessCode("BIZ-001"), new ActionCode("ACT-VIEW"))).isTrue();
      assertThat(perm.covers(new BusinessCode("BIZ-001"), new ActionCode("ACT-EDIT"))).isFalse();
    }

    @Test
    @DisplayName("业务编码不匹配应返回 false")
    void shouldReturnFalseWhenBusinessDiffers() {
      var perm = new Permission(new BusinessCode("BIZ-001"), new ActionCode("ACT-VIEW"));
      assertThat(perm.covers(new BusinessCode("BIZ-999"), new ActionCode("ACT-VIEW"))).isFalse();
    }
  }
}
