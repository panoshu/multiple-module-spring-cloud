package com.pension.permission.domain.permission.aggregate;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.permission.enumeration.PermissionItemSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionItemTest {

  @Test
  void create_should_set_basic_fields_and_emit_created_event() {
    PermissionItem item = PermissionItem.create(
      "USER_MANAGE", "FREEZE", PermissionCategory.PLATFORM,
      PermissionItemSource.API, "UserController", "freezeUser",
      "POST", "/api/users/freeze", UserNo.of("admin-1"));

    assertThat(item.businessCode().value()).isEqualTo("USER_MANAGE");
    assertThat(item.actionCode().value()).isEqualTo("FREEZE");
    assertThat(item.category()).isEqualTo(PermissionCategory.PLATFORM);
    assertThat(item.source()).isEqualTo(PermissionItemSource.API);
    assertThat(item.autoRegistered()).isTrue();
    assertThat(item.createdBy().value()).isEqualTo("admin-1");
    assertThat(item.domainEvents()).hasSize(1);
  }

  @Test
  void create_with_null_action_should_mean_whole_business() {
    PermissionItem item = PermissionItem.create(
      "PLAN_QUERY", null, PermissionCategory.BUSINESS,
      PermissionItemSource.API, "PlanController", "list",
      "GET", "/api/plans", UserNo.of("admin-1"));

    assertThat(item.actionCode()).isNull();
  }

  @Test
  void reconstitute_should_not_emit_event() {
    PermissionItem item = PermissionItem.reconstitute(
      "item-1", "USER_MANAGE", "FREEZE", PermissionCategory.PLATFORM,
      PermissionItemSource.API, "UserController", "freezeUser",
      "POST", "/api/users/freeze", "冻结用户", "用户管理", 0, true,
      UserNo.of("admin-1"), UserNo.of("admin-2"),
      java.time.LocalDateTime.now(), java.time.LocalDateTime.now());

    assertThat(item.domainEvents()).isEmpty();
  }

  @Test
  void update_metadata_should_set_display_name_and_group() {
    PermissionItem item = PermissionItem.create(
      "USER_MANAGE", "FREEZE", PermissionCategory.PLATFORM,
      PermissionItemSource.API, "UserController", "freezeUser",
      "POST", "/api/users/freeze", UserNo.of("admin-1"));

    item.updateMetadata("冻结用户", "用户管理", 10, UserNo.of("admin-2"));

    assertThat(item.displayName()).isEqualTo("冻结用户");
    assertThat(item.categoryGroup()).isEqualTo("用户管理");
    assertThat(item.sortOrder()).isEqualTo(10);
  }

  @Test
  void mark_stale_should_set_auto_registered_false() {
    PermissionItem item = PermissionItem.create(
      "USER_MANAGE", "FREEZE", PermissionCategory.PLATFORM,
      PermissionItemSource.API, "UserController", "freezeUser",
      "POST", "/api/users/freeze", UserNo.of("admin-1"));

    item.markStale(UserNo.of("scanner"));

    assertThat(item.autoRegistered()).isFalse();
  }
}
