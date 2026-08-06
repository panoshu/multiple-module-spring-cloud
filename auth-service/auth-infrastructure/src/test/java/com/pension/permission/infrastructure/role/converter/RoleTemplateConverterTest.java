package com.pension.permission.infrastructure.role.converter;

import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.UserNo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.role.aggregate.RoleTemplate;
import com.pension.permission.domain.role.enumeration.RoleTemplateScopeDimension;
import com.pension.permission.domain.role.enumeration.RoleTemplateStatus;
import com.pension.permission.infrastructure.role.entity.RoleTemplateDO;
import com.pension.permission.types.RoleCode;
import com.pension.permission.types.RoleTemplateId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoleTemplateConverter 转换器测试")
class RoleTemplateConverterTest {

  private RoleTemplateConverter converter;

  @BeforeEach
  void setUp() throws Exception {
    converter = new RoleTemplateConverterImpl();
    Field field = RoleTemplateConverter.class.getDeclaredField("objectMapper");
    field.setAccessible(true);
    field.set(converter, new ObjectMapper());
  }

  private RoleTemplate sampleTemplate(
    RoleTemplateScopeDimension dimension,
    String scopeValue,
    Set<Permission> permissions,
    RoleTemplateStatus status
  ) {
    return RoleTemplate.reconstitute(
      new RoleTemplateId("rt-001"),
      UserNo.of("creator-1"),
      UserNo.of("updater-1"),
      LocalDateTime.of(2026, 1, 1, 10, 0),
      LocalDateTime.of(2026, 1, 2, 10, 0),
      Version.of(3L),
      new RoleCode("ROLE_AGENT"),
      dimension,
      scopeValue,
      permissions,
      status
    );
  }

  private Set<Permission> samplePermissions() {
    return Set.of(new Permission(new BusinessCode("BIZ-001"), new ActionCode("ACT-VIEW")));
  }

  @Nested
  @DisplayName("toDO: 领域对象 → DO")
  class ToDOTest {

    @Test
    @DisplayName("null 输入应返回 null")
    void shouldReturnNullWhenNullInput() {
      assertThat(converter.toDO(null)).isNull();
    }

    @Test
    @DisplayName("应正确映射业务字段")
    void shouldMapBusinessFields() {
      var template = sampleTemplate(RoleTemplateScopeDimension.PLAN, "PLAN-001", samplePermissions(), RoleTemplateStatus.EFFECTIVE);

      var doObj = converter.toDO(template);

      assertThat(doObj.getId()).isEqualTo("rt-001");
      assertThat(doObj.getRoleCode()).isEqualTo("ROLE_AGENT");
      assertThat(doObj.getScopeDimension()).isEqualTo("PLAN");
      assertThat(doObj.getScopeValue()).isEqualTo("PLAN-001");
      assertThat(doObj.getStatus()).isEqualTo("EFFECTIVE");
    }

    @Test
    @DisplayName("应正确映射基类字段")
    void shouldMapBaseFields() {
      var template = sampleTemplate(RoleTemplateScopeDimension.PLAN, "PLAN-001", samplePermissions(), RoleTemplateStatus.EFFECTIVE);

      var doObj = converter.toDO(template);

      assertThat(doObj.getCreatedBy()).isEqualTo("creator-1");
      assertThat(doObj.getUpdatedBy()).isEqualTo("updater-1");
      assertThat(doObj.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
      assertThat(doObj.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
      assertThat(doObj.getVersion()).isEqualTo(3);
      assertThat(doObj.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("应序列化 Permission 集合为 JSON")
    void shouldSerializePermissions() {
      var template = sampleTemplate(RoleTemplateScopeDimension.PLAN, "PLAN-001", samplePermissions(), RoleTemplateStatus.EFFECTIVE);

      var doObj = converter.toDO(template);

      assertThat(doObj.getPermissions()).contains("BIZ-001");
      assertThat(doObj.getPermissions()).contains("ACT-VIEW");
    }

    @Test
    @DisplayName("空 Permission 集合应序列化为 null")
    void shouldReturnNullPermissionsJsonWhenEmpty() {
      var template = sampleTemplate(RoleTemplateScopeDimension.PLAN, "PLAN-001", Set.of(), RoleTemplateStatus.EFFECTIVE);

      var doObj = converter.toDO(template);

      assertThat(doObj.getPermissions()).isNull();
    }

    @Test
    @DisplayName("GLOBAL 维度应将 scopeValue 映射为 null")
    void shouldMapNullScopeValueForGlobal() {
      var template = sampleTemplate(RoleTemplateScopeDimension.GLOBAL, null, samplePermissions(), RoleTemplateStatus.EFFECTIVE);

      var doObj = converter.toDO(template);

      assertThat(doObj.getScopeDimension()).isEqualTo("GLOBAL");
      assertThat(doObj.getScopeValue()).isNull();
    }
  }

  @Nested
  @DisplayName("toDomain: DO → 领域对象")
  class ToDomainTest {

    @Test
    @DisplayName("null 输入应返回 null")
    void shouldReturnNullWhenNullInput() {
      assertThat(converter.toDomain(null)).isNull();
    }

    @Test
    @DisplayName("应正确映射业务字段")
    void shouldMapBusinessFields() throws Exception {
      // 使用与 Converter 相同的 Jackson 序列化方式生成 permissions JSON，避免手写格式不一致
      String permissionsJson = new ObjectMapper().writeValueAsString(samplePermissions());

      var doObj = new RoleTemplateDO();
      doObj.setId("rt-001");
      doObj.setRoleCode("ROLE_AGENT");
      doObj.setScopeDimension("PLAN");
      doObj.setScopeValue("PLAN-001");
      doObj.setPermissions(permissionsJson);
      doObj.setStatus("EFFECTIVE");
      doObj.setCreatedBy("creator-1");
      doObj.setCreateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
      doObj.setUpdatedBy("updater-1");
      doObj.setUpdateTime(LocalDateTime.of(2026, 1, 2, 10, 0));
      doObj.setVersion(3);

      var template = converter.toDomain(doObj);

      assertThat(template.id().value()).isEqualTo("rt-001");
      assertThat(template.roleCode()).isEqualTo(new RoleCode("ROLE_AGENT"));
      assertThat(template.scopeDimension()).isEqualTo(RoleTemplateScopeDimension.PLAN);
      assertThat(template.scopeValue()).isEqualTo("PLAN-001");
      assertThat(template.status()).isEqualTo(RoleTemplateStatus.EFFECTIVE);
      assertThat(template.permissions()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("往返一致性")
  class RoundTripTest {

    @Test
    @DisplayName("PLAN 维度模板应可完整往返")
    void shouldRoundTripPlanTemplate() {
      var original = sampleTemplate(RoleTemplateScopeDimension.PLAN, "PLAN-001", samplePermissions(), RoleTemplateStatus.EFFECTIVE);

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.id().value()).isEqualTo("rt-001");
      assertThat(roundTripped.roleCode()).isEqualTo(new RoleCode("ROLE_AGENT"));
      assertThat(roundTripped.scopeDimension()).isEqualTo(RoleTemplateScopeDimension.PLAN);
      assertThat(roundTripped.scopeValue()).isEqualTo("PLAN-001");
      assertThat(roundTripped.status()).isEqualTo(RoleTemplateStatus.EFFECTIVE);
      assertThat(roundTripped.permissions()).hasSize(1);
      assertThat(roundTripped.permissions()).contains(
        new Permission(new BusinessCode("BIZ-001"), new ActionCode("ACT-VIEW")));
      assertThat(roundTripped.version().value()).isEqualTo(3L);
    }

    @Test
    @DisplayName("GLOBAL 维度模板应可完整往返且 scopeValue 保持 null")
    void shouldRoundTripGlobalTemplate() {
      var original = sampleTemplate(RoleTemplateScopeDimension.GLOBAL, null, samplePermissions(), RoleTemplateStatus.INACTIVE);

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.scopeDimension()).isEqualTo(RoleTemplateScopeDimension.GLOBAL);
      assertThat(roundTripped.scopeValue()).isNull();
      assertThat(roundTripped.status()).isEqualTo(RoleTemplateStatus.INACTIVE);
      assertThat(roundTripped.permissions()).hasSize(1);
    }

    @Test
    @DisplayName("空权限集合应可完整往返")
    void shouldRoundTripEmptyPermissions() {
      var original = sampleTemplate(RoleTemplateScopeDimension.PLAN, "PLAN-001", Set.of(), RoleTemplateStatus.DRAFT);

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.permissions()).isEmpty();
      assertThat(roundTripped.status()).isEqualTo(RoleTemplateStatus.DRAFT);
    }
  }
}
