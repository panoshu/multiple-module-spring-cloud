package com.pension.permission.domain.role.aggregate;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.fixture.AuthorizationFixtures;
import com.pension.permission.domain.role.enumeration.RoleTemplateScopeDimension;
import com.pension.permission.domain.role.enumeration.RoleTemplateStatus;
import com.pension.permission.types.RoleCode;
import com.pension.permission.types.RoleTemplateId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RoleTemplate 聚合根测试")
class RoleTemplateTest {

  private RoleTemplate effectiveTemplate() {
    return RoleTemplate.create(
      new RoleTemplateId("rt-1"),
      UserNo.of("creator-1"),
      new RoleCode("ROLE_AGENT"),
      RoleTemplateScopeDimension.PLAN,
      "PLAN-001",
      Set.of(AuthorizationFixtures.permission("BIZ-001", "ACT-VIEW")),
      RoleTemplateStatus.EFFECTIVE);
  }

  @Nested
  @DisplayName("创建 create")
  class CreateTest {

    @Test
    @DisplayName("应创建指定状态的角色模板")
    void shouldCreateTemplate() {
      var template = effectiveTemplate();

      assertThat(template.roleCode()).isEqualTo(new RoleCode("ROLE_AGENT"));
      assertThat(template.isActive()).isTrue();
      assertThat(template.permissions()).hasSize(1);
    }

    @Test
    @DisplayName("GLOBAL 维度下 scopeValue 非 null 应抛异常")
    void shouldThrowWhenGlobalHasScopeValue() {
      assertThatThrownBy(() -> RoleTemplate.create(
        new RoleTemplateId("rt-2"),
        UserNo.of("creator-1"),
        new RoleCode("ROLE_AGENT"),
        RoleTemplateScopeDimension.GLOBAL,
        "PLAN-001",
        Set.of(),
        RoleTemplateStatus.EFFECTIVE))
        .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("非 GLOBAL 维度下 scopeValue 为 null 应抛异常")
    void shouldThrowWhenNonGlobalHasNullScopeValue() {
      assertThatThrownBy(() -> RoleTemplate.create(
        new RoleTemplateId("rt-3"),
        UserNo.of("creator-1"),
        new RoleCode("ROLE_AGENT"),
        RoleTemplateScopeDimension.PLAN,
        null,
        Set.of(),
        RoleTemplateStatus.EFFECTIVE))
        .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("状态流转")
  class StatusTransitionTest {

    @Test
    @DisplayName("activate 已激活模板应幂等")
    void shouldBeIdempotentWhenActivateActiveTemplate() {
      var template = effectiveTemplate();

      template.activate(UserNo.of("user-1"));

      assertThat(template.status()).isEqualTo(RoleTemplateStatus.EFFECTIVE);
    }

    @Test
    @DisplayName("deactivate 应转为 INACTIVE")
    void shouldDeactivateTemplate() {
      var template = effectiveTemplate();

      template.deactivate(UserNo.of("user-1"));

      assertThat(template.status()).isEqualTo(RoleTemplateStatus.INACTIVE);
      assertThat(template.isActive()).isFalse();
    }

    @Test
    @DisplayName("deactivate 已停用模板应幂等")
    void shouldBeIdempotentWhenDeactivateInactiveTemplate() {
      var template = effectiveTemplate();
      template.deactivate(UserNo.of("user-1"));

      template.deactivate(UserNo.of("user-2"));

      assertThat(template.status()).isEqualTo(RoleTemplateStatus.INACTIVE);
    }

    @Test
    @DisplayName("停用后再激活应转为 EFFECTIVE")
    void shouldReactivateInactiveTemplate() {
      var template = effectiveTemplate();
      template.deactivate(UserNo.of("user-1"));

      template.activate(UserNo.of("user-2"));

      assertThat(template.status()).isEqualTo(RoleTemplateStatus.EFFECTIVE);
      assertThat(template.isActive()).isTrue();
    }
  }

  @Nested
  @DisplayName("权限判定")
  class PermissionCheckTest {

    @Test
    @DisplayName("hasPermission 匹配已有权限应返回 true")
    void shouldReturnTrueWhenPermissionExists() {
      var template = effectiveTemplate();
      var perm = AuthorizationFixtures.permission("BIZ-001", "ACT-VIEW");

      assertThat(template.hasPermission(perm)).isTrue();
    }

    @Test
    @DisplayName("hasPermission 不匹配应返回 false")
    void shouldReturnFalseWhenPermissionNotExists() {
      var template = effectiveTemplate();
      var perm = AuthorizationFixtures.permission("BIZ-999", "ACT-VIEW");

      assertThat(template.hasPermission(perm)).isFalse();
    }
  }

  @Nested
  @DisplayName("作用域匹配 matchesScope")
  class ScopeMatchTest {

    @Test
    @DisplayName("同维度同 scopeValue 应返回 true")
    void shouldReturnTrueWhenSameDimensionAndValue() {
      var template = effectiveTemplate();

      assertThat(template.matchesScope(RoleTemplateScopeDimension.PLAN, "PLAN-001")).isTrue();
    }

    @Test
    @DisplayName("同维度不同 scopeValue 应返回 false")
    void shouldReturnFalseWhenSameDimensionDifferentValue() {
      var template = effectiveTemplate();

      assertThat(template.matchesScope(RoleTemplateScopeDimension.PLAN, "PLAN-999")).isFalse();
    }

    @Test
    @DisplayName("不同维度应返回 false")
    void shouldReturnFalseWhenDifferentDimension() {
      var template = effectiveTemplate();

      assertThat(template.matchesScope(RoleTemplateScopeDimension.CUSTOMER, "CUST-001")).isFalse();
    }
  }
}
