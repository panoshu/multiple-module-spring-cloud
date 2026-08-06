package com.pension.permission.infrastructure.assignment.converter;

import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.assignment.aggregate.AgentIdentityAssignment;
import com.pension.permission.domain.assignment.enumeration.AssignmentStatus;
import com.pension.permission.infrastructure.assignment.entity.AssignmentDO;
import com.pension.permission.types.AssignmentId;
import com.pension.permission.types.AssignmentScopeDimension;
import com.pension.permission.types.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AssignmentConverter 转换器测试")
class AssignmentConverterTest {

  private AssignmentConverter converter;

  @BeforeEach
  void setUp() {
    converter = new AssignmentConverterImpl();
  }

  private AgentIdentityAssignment sampleAssignment(
    AssignmentScopeDimension dimension,
    String scopeValue,
    boolean inheritable,
    AssignmentStatus status
  ) {
    return AgentIdentityAssignment.reconstitute(
      new AssignmentId("a-001"),
      UserNo.of("creator-1"),
      UserNo.of("updater-1"),
      LocalDateTime.of(2026, 1, 1, 10, 0),
      LocalDateTime.of(2026, 1, 2, 10, 0),
      Version.of(3L),
      UserNo.of("user-001"),
      new RoleCode("ROLE_AGENT"),
      dimension,
      scopeValue,
      inheritable,
      status
    );
  }

  private AssignmentDO sampleDO() {
    AssignmentDO doObj = new AssignmentDO();
    doObj.setId("a-001");
    doObj.setUserNo("user-001");
    doObj.setRoleCode("ROLE_AGENT");
    doObj.setScopeDimension("PLAN");
    doObj.setScopeValue("PLAN-001");
    doObj.setInheritable(false);
    doObj.setStatus("ACTIVE");
    doObj.setCreatedBy("creator-1");
    doObj.setUpdatedBy("updater-1");
    doObj.setCreateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
    doObj.setUpdateTime(LocalDateTime.of(2026, 1, 2, 10, 0));
    doObj.setVersion(3);
    doObj.setDeleted(false);
    return doObj;
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
      var assignment = sampleAssignment(AssignmentScopeDimension.PLAN, "PLAN-001", false, AssignmentStatus.ACTIVE);

      var doObj = converter.toDO(assignment);

      assertThat(doObj.getId()).isEqualTo("a-001");
      assertThat(doObj.getUserNo()).isEqualTo("user-001");
      assertThat(doObj.getRoleCode()).isEqualTo("ROLE_AGENT");
      assertThat(doObj.getScopeDimension()).isEqualTo("PLAN");
      assertThat(doObj.getScopeValue()).isEqualTo("PLAN-001");
      assertThat(doObj.getInheritable()).isFalse();
      assertThat(doObj.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("应正确映射基类字段")
    void shouldMapBaseFields() {
      var assignment = sampleAssignment(AssignmentScopeDimension.PLAN, "PLAN-001", false, AssignmentStatus.ACTIVE);

      var doObj = converter.toDO(assignment);

      assertThat(doObj.getCreatedBy()).isEqualTo("creator-1");
      assertThat(doObj.getUpdatedBy()).isEqualTo("updater-1");
      assertThat(doObj.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
      assertThat(doObj.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
      assertThat(doObj.getVersion()).isEqualTo(3);
      assertThat(doObj.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("已停用分配应映射为 DEACTIVATED 状态")
    void shouldMapDeactivatedStatus() {
      var assignment = sampleAssignment(AssignmentScopeDimension.PLAN, "PLAN-001", false, AssignmentStatus.DEACTIVATED);

      var doObj = converter.toDO(assignment);

      assertThat(doObj.getStatus()).isEqualTo("DEACTIVATED");
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
    void shouldMapBusinessFields() {
      var doObj = sampleDO();

      var assignment = converter.toDomain(doObj);

      assertThat(assignment.id().value()).isEqualTo("a-001");
      assertThat(assignment.userNo()).isEqualTo(UserNo.of("user-001"));
      assertThat(assignment.roleCode()).isEqualTo(new RoleCode("ROLE_AGENT"));
      assertThat(assignment.scopeDimension()).isEqualTo(AssignmentScopeDimension.PLAN);
      assertThat(assignment.scopeValue()).isEqualTo("PLAN-001");
      assertThat(assignment.isInheritable()).isFalse();
      assertThat(assignment.isActive()).isTrue();
    }

    @Test
    @DisplayName("应正确映射基类字段")
    void shouldMapBaseFields() {
      var doObj = sampleDO();

      var assignment = converter.toDomain(doObj);

      assertThat(assignment.createdBy()).isEqualTo(UserNo.of("creator-1"));
      assertThat(assignment.updatedBy()).isEqualTo(UserNo.of("updater-1"));
      assertThat(assignment.createdAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
      assertThat(assignment.updatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
      assertThat(assignment.version().value()).isEqualTo(3L);
    }
  }

  @Nested
  @DisplayName("往返一致性")
  class RoundTripTest {

    @Test
    @DisplayName("激活状态的分配应可完整往返")
    void shouldRoundTripActiveAssignment() {
      var original = sampleAssignment(AssignmentScopeDimension.PLAN, "PLAN-001", false, AssignmentStatus.ACTIVE);

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.id().value()).isEqualTo("a-001");
      assertThat(roundTripped.userNo()).isEqualTo(UserNo.of("user-001"));
      assertThat(roundTripped.roleCode()).isEqualTo(new RoleCode("ROLE_AGENT"));
      assertThat(roundTripped.scopeDimension()).isEqualTo(AssignmentScopeDimension.PLAN);
      assertThat(roundTripped.scopeValue()).isEqualTo("PLAN-001");
      assertThat(roundTripped.isInheritable()).isFalse();
      assertThat(roundTripped.isActive()).isTrue();
      assertThat(roundTripped.version().value()).isEqualTo(3L);
    }

    @Test
    @DisplayName("已停用状态的分配应可完整往返")
    void shouldRoundTripDeactivatedAssignment() {
      var original = sampleAssignment(AssignmentScopeDimension.PRODUCT, "PRODUCT-001", false, AssignmentStatus.DEACTIVATED);

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.scopeDimension()).isEqualTo(AssignmentScopeDimension.PRODUCT);
      assertThat(roundTripped.scopeValue()).isEqualTo("PRODUCT-001");
      assertThat(roundTripped.isActive()).isFalse();
    }

    @Test
    @DisplayName("CUSTOMER 维度可继承分配应可完整往返")
    void shouldRoundTripInheritableCustomerAssignment() {
      var original = sampleAssignment(AssignmentScopeDimension.CUSTOMER, "CUSTOMER-001", true, AssignmentStatus.ACTIVE);

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.scopeDimension()).isEqualTo(AssignmentScopeDimension.CUSTOMER);
      assertThat(roundTripped.scopeValue()).isEqualTo("CUSTOMER-001");
      assertThat(roundTripped.isInheritable()).isTrue();
      assertThat(roundTripped.isActive()).isTrue();
    }
  }
}
