package com.pension.permission.infrastructure.role.converter;

import com.pension.permission.domain.role.enumeration.RoleTemplateScopeDimension;
import com.pension.permission.domain.role.enumeration.RoleVisibilityMode;
import com.pension.permission.domain.role.valueobject.RoleVisibilityScope;
import com.pension.permission.infrastructure.role.entity.RoleVisibilityDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RoleVisibilityConverter 转换器测试")
class RoleVisibilityConverterTest {

  private RoleVisibilityConverter converter;

  @BeforeEach
  void setUp() {
    converter = new RoleVisibilityConverterImpl();
  }

  @Nested
  @DisplayName("toDO: 值对象 → DO")
  class ToDOTest {

    @Test
    @DisplayName("null 输入应返回 null")
    void shouldReturnNullWhenNullInput() {
      assertThat(converter.toDO(null)).isNull();
    }

    @Test
    @DisplayName("应正确映射业务字段")
    void shouldMapBusinessFields() {
      var scope = new RoleVisibilityScope(
        RoleTemplateScopeDimension.PLAN, "PLAN-001", RoleVisibilityMode.SHOW_ALL);

      var doObj = converter.toDO(scope);

      assertThat(doObj.getDimension()).isEqualTo("PLAN");
      assertThat(doObj.getScopeValue()).isEqualTo("PLAN-001");
      assertThat(doObj.getMode()).isEqualTo("SHOW_ALL");
    }

    @Test
    @DisplayName("不应映射值对象不携带的基类字段")
    void shouldNotMapBaseFields() {
      var scope = new RoleVisibilityScope(
        RoleTemplateScopeDimension.PLAN, "PLAN-001", RoleVisibilityMode.SHOW_ALL);

      var doObj = converter.toDO(scope);

      assertThat(doObj.getId()).isNull();
      assertThat(doObj.getCreatedBy()).isNull();
      assertThat(doObj.getUpdatedBy()).isNull();
      assertThat(doObj.getCreateTime()).isNull();
      assertThat(doObj.getUpdateTime()).isNull();
      assertThat(doObj.getVersion()).isNull();
      assertThat(doObj.getDeleted()).isNull();
    }
  }

  @Nested
  @DisplayName("toDomain: DO → 值对象")
  class ToDomainTest {

    @Test
    @DisplayName("null 输入应返回 null")
    void shouldReturnNullWhenNullInput() {
      assertThat(converter.toDomain(null)).isNull();
    }

    @Test
    @DisplayName("应正确映射业务字段")
    void shouldMapBusinessFields() {
      var doObj = new RoleVisibilityDO();
      doObj.setDimension("CUSTOMER");
      doObj.setScopeValue("CUSTOMER-001");
      doObj.setMode("EXCLUSIVE_ONLY");

      var scope = converter.toDomain(doObj);

      assertThat(scope.dimension()).isEqualTo(RoleTemplateScopeDimension.CUSTOMER);
      assertThat(scope.value()).isEqualTo("CUSTOMER-001");
      assertThat(scope.mode()).isEqualTo(RoleVisibilityMode.EXCLUSIVE_ONLY);
    }
  }

  @Nested
  @DisplayName("往返一致性")
  class RoundTripTest {

    @Test
    @DisplayName("PLAN + SHOW_ALL 应可完整往返")
    void shouldRoundTripPlanShowAll() {
      var original = new RoleVisibilityScope(
        RoleTemplateScopeDimension.PLAN, "PLAN-001", RoleVisibilityMode.SHOW_ALL);

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.dimension()).isEqualTo(RoleTemplateScopeDimension.PLAN);
      assertThat(roundTripped.value()).isEqualTo("PLAN-001");
      assertThat(roundTripped.mode()).isEqualTo(RoleVisibilityMode.SHOW_ALL);
    }

    @Test
    @DisplayName("CUSTOMER + EXCLUSIVE_ONLY 应可完整往返")
    void shouldRoundTripCustomerExclusiveOnly() {
      var original = new RoleVisibilityScope(
        RoleTemplateScopeDimension.CUSTOMER, "CUSTOMER-001", RoleVisibilityMode.EXCLUSIVE_ONLY);

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.dimension()).isEqualTo(RoleTemplateScopeDimension.CUSTOMER);
      assertThat(roundTripped.value()).isEqualTo("CUSTOMER-001");
      assertThat(roundTripped.mode()).isEqualTo(RoleVisibilityMode.EXCLUSIVE_ONLY);
    }
  }
}
