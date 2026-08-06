package com.pension.permission.infrastructure.authorization.converter;

import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.valueobject.ValidityPeriod;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pension.permission.domain.authorization.aggregate.Grant;
import com.pension.permission.domain.authorization.enumeration.Effect;
import com.pension.permission.domain.authorization.enumeration.GrantOrigin;
import com.pension.permission.domain.authorization.enumeration.GrantStatus;
import com.pension.permission.domain.authorization.enumeration.GrantType;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.authorization.enumeration.ScopeDimension;
import com.pension.permission.domain.authorization.valueobject.ScopeRule;
import com.pension.permission.domain.authorization.valueobject.subject.CapabilitySubject;
import com.pension.permission.domain.authorization.valueobject.subject.GrantSubject;
import com.pension.permission.domain.authorization.valueobject.subject.PlanAllMembersSubject;
import com.pension.permission.domain.authorization.valueobject.subject.PlanRoleSubject;
import com.pension.permission.domain.authorization.valueobject.subject.UserListSubject;
import com.pension.permission.infrastructure.authorization.entity.GrantDO;
import com.pension.permission.types.GrantId;
import com.pension.permission.types.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GrantConverter 转换器测试")
class GrantConverterTest {

  private GrantConverter converter;

  @BeforeEach
  void setUp() throws Exception {
    converter = new GrantConverterImpl();
    Field field = GrantConverter.class.getDeclaredField("objectMapper");
    field.setAccessible(true);
    field.set(converter, new ObjectMapper());
  }

  private Grant sampleGrant(GrantSubject subject) {
    return Grant.reconstitute(
      new GrantId("g-001"),
      UserNo.of("creator-1"),
      UserNo.of("updater-1"),
      LocalDateTime.of(2026, 1, 1, 10, 0),
      LocalDateTime.of(2026, 1, 2, 10, 0),
      Version.of(3L),
      subject,
      List.of(ScopeRule.of(ScopeDimension.PLAN, "PLAN-001")),
      Set.of(new Permission(new BusinessCode("BIZ-001"), new ActionCode("ACT-VIEW"))),
      GrantType.BASE,
      GrantOrigin.HQ_CONFIG,
      Effect.ALLOW,
      GrantStatus.EFFECTIVE,
      ValidityPeriod.between(
        LocalDateTime.of(2026, 1, 1, 0, 0),
        LocalDateTime.of(2026, 12, 31, 23, 59)),
      PlanNo.of("PLAN-SRC"),
      PlanNo.of("PLAN-TGT"));
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
    @DisplayName("应正确映射基础字段")
    void shouldMapBasicFields() {
      var grant = sampleGrant(new CapabilitySubject());

      var doObj = converter.toDO(grant);

      assertThat(doObj.getId()).isEqualTo("g-001");
      assertThat(doObj.getGrantType()).isEqualTo("BASE");
      assertThat(doObj.getOrigin()).isEqualTo("HQ_CONFIG");
      assertThat(doObj.getEffect()).isEqualTo("ALLOW");
      assertThat(doObj.getStatus()).isEqualTo("EFFECTIVE");
      assertThat(doObj.getSourcePlanNo()).isEqualTo("PLAN-SRC");
      assertThat(doObj.getTargetPlanNo()).isEqualTo("PLAN-TGT");
    }

    @Test
    @DisplayName("应正确映射基类字段")
    void shouldMapBaseFields() {
      var grant = sampleGrant(new CapabilitySubject());

      var doObj = converter.toDO(grant);

      assertThat(doObj.getCreatedBy()).isEqualTo("creator-1");
      assertThat(doObj.getUpdatedBy()).isEqualTo("updater-1");
      assertThat(doObj.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
      assertThat(doObj.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
      assertThat(doObj.getVersion()).isEqualTo(3);
      assertThat(doObj.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("应正确映射 ValidityPeriod")
    void shouldMapValidityPeriod() {
      var grant = sampleGrant(new CapabilitySubject());

      var doObj = converter.toDO(grant);

      assertThat(doObj.getValidityStart()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
      assertThat(doObj.getValidityEnd()).isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59));
    }

    @Test
    @DisplayName("应序列化 ScopeRule 列表为 JSON")
    void shouldSerializeScopeRules() {
      var grant = sampleGrant(new CapabilitySubject());

      var doObj = converter.toDO(grant);

      assertThat(doObj.getScopeRules()).contains("PLAN-001");
    }

    @Test
    @DisplayName("应序列化 Permission 集合为 JSON")
    void shouldSerializePermissions() {
      var grant = sampleGrant(new CapabilitySubject());

      var doObj = converter.toDO(grant);

      assertThat(doObj.getPermissions()).contains("BIZ-001");
      assertThat(doObj.getPermissions()).contains("ACT-VIEW");
    }
  }

  @Nested
  @DisplayName("toDO: GrantSubject 多态序列化")
  class SubjectSerializationTest {

    @Test
    @DisplayName("CapabilitySubject 应序列化包含 @type=Capability")
    void shouldSerializeCapabilitySubject() {
      var grant = sampleGrant(new CapabilitySubject());

      var doObj = converter.toDO(grant);

      assertThat(doObj.getSubject()).contains("Capability");
    }

    @Test
    @DisplayName("UserListSubject 应序列化包含 @type=UserList 和 accountIds")
    void shouldSerializeUserListSubject() {
      var grant = sampleGrant(
        new UserListSubject(Set.of(UserNo.of("u-1"), UserNo.of("u-2"))));

      var doObj = converter.toDO(grant);

      assertThat(doObj.getSubject()).contains("UserList");
      assertThat(doObj.getSubject()).contains("u-1");
      assertThat(doObj.getSubject()).contains("u-2");
    }

    @Test
    @DisplayName("PlanAllMembersSubject 应序列化包含 @type=PlanAllMembers 和 planNo")
    void shouldSerializePlanAllMembersSubject() {
      var grant = sampleGrant(new PlanAllMembersSubject(PlanNo.of("PLAN-001")));

      var doObj = converter.toDO(grant);

      assertThat(doObj.getSubject()).contains("PlanAllMembers");
      assertThat(doObj.getSubject()).contains("PLAN-001");
    }

    @Test
    @DisplayName("PlanRoleSubject 应序列化包含 @type=PlanRole 和 planNo/roleCode")
    void shouldSerializePlanRoleSubject() {
      var grant = sampleGrant(
        new PlanRoleSubject(PlanNo.of("PLAN-001"), new RoleCode("ROLE_AGENT")));

      var doObj = converter.toDO(grant);

      assertThat(doObj.getSubject()).contains("PlanRole");
      assertThat(doObj.getSubject()).contains("PLAN-001");
      assertThat(doObj.getSubject()).contains("ROLE_AGENT");
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
    @DisplayName("应正确往返 CapabilitySubject")
    void shouldRoundTripCapabilitySubject() {
      var original = sampleGrant(new CapabilitySubject());

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.subject()).isInstanceOf(CapabilitySubject.class);
      assertThat(roundTripped.id()).isEqualTo(original.id());
      assertThat(roundTripped.effect()).isEqualTo(original.effect());
      assertThat(roundTripped.status()).isEqualTo(original.status());
    }

    @Test
    @DisplayName("应正确往返 UserListSubject")
    void shouldRoundTripUserListSubject() {
      var original = sampleGrant(
        new UserListSubject(Set.of(UserNo.of("u-1"), UserNo.of("u-2"))));

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.subject()).isInstanceOf(UserListSubject.class);
      var subject = (UserListSubject) roundTripped.subject();
      assertThat(subject.accountIds()).containsExactlyInAnyOrder(UserNo.of("u-1"), UserNo.of("u-2"));
    }

    @Test
    @DisplayName("应正确往返 PlanAllMembersSubject")
    void shouldRoundTripPlanAllMembersSubject() {
      var original = sampleGrant(new PlanAllMembersSubject(PlanNo.of("PLAN-001")));

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.subject()).isInstanceOf(PlanAllMembersSubject.class);
      assertThat(((PlanAllMembersSubject) roundTripped.subject()).planNo()).isEqualTo(PlanNo.of("PLAN-001"));
    }

    @Test
    @DisplayName("应正确往返 PlanRoleSubject")
    void shouldRoundTripPlanRoleSubject() {
      var original = sampleGrant(
        new PlanRoleSubject(PlanNo.of("PLAN-001"), new RoleCode("ROLE_AGENT")));

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.subject()).isInstanceOf(PlanRoleSubject.class);
      var subject = (PlanRoleSubject) roundTripped.subject();
      assertThat(subject.planNo()).isEqualTo(PlanNo.of("PLAN-001"));
      assertThat(subject.roleCode()).isEqualTo(new RoleCode("ROLE_AGENT"));
    }

    @Test
    @DisplayName("应正确往返 ScopeRule 列表")
    void shouldRoundTripScopeRules() {
      var original = sampleGrant(new CapabilitySubject());

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.scopeRules()).hasSize(1);
      assertThat(roundTripped.scopeRules().get(0).dimension()).isEqualTo(ScopeDimension.PLAN);
      assertThat(roundTripped.scopeRules().get(0).value()).isEqualTo("PLAN-001");
    }

    @Test
    @DisplayName("应正确往返 Permission 集合")
    void shouldRoundTripPermissions() {
      var original = sampleGrant(new CapabilitySubject());

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.permissions()).hasSize(1);
    }

    @Test
    @DisplayName("应正确往返 ValidityPeriod")
    void shouldRoundTripValidityPeriod() {
      var original = sampleGrant(new CapabilitySubject());

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.validityPeriod().start()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
      assertThat(roundTripped.validityPeriod().end()).isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59));
    }
  }

  @Nested
  @DisplayName("toValidityPeriod 边界场景")
  class ValidityPeriodEdgeTest {

    @Test
    @DisplayName("start 和 end 均为 null 应返回 infinite")
    void shouldReturnInfiniteWhenBothNull() {
      var grant = sampleGrant(new CapabilitySubject());

      var doObj = converter.toDO(grant);
      doObj.setValidityStart(null);
      doObj.setValidityEnd(null);

      var roundTripped = converter.toDomain(doObj);

      assertThat(roundTripped.validityPeriod().start()).isNull();
      assertThat(roundTripped.validityPeriod().end()).isNull();
    }

    @Test
    @DisplayName("仅 start 有值应返回 since")
    void shouldReturnSinceWhenOnlyStart() {
      var grant = sampleGrant(new CapabilitySubject());

      var doObj = converter.toDO(grant);
      doObj.setValidityEnd(null);

      var roundTripped = converter.toDomain(doObj);

      assertThat(roundTripped.validityPeriod().start()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
      assertThat(roundTripped.validityPeriod().end()).isNull();
    }
  }
}
