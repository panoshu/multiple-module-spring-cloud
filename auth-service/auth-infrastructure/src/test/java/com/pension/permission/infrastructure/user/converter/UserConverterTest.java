package com.pension.permission.infrastructure.user.converter;

import com.example.shared.contactinfo.*;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.identity.DocumentNumber;
import com.example.shared.identity.IdentityDocument;
import com.example.shared.identity.IdentityType;
import com.pension.permission.domain.user.aggregate.UserAggregate;
import com.pension.permission.domain.user.enumeration.UserStatus;
import com.pension.permission.domain.user.enumeration.UserType;
import com.pension.permission.infrastructure.user.entity.UserDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserConverter 转换器测试")
class UserConverterTest {

  private UserConverter converter;

  @BeforeEach
  void setUp() {
    converter = new UserConverterImpl();
  }

  private UserAggregate fullUser() {
    return UserAggregate.restore(
      UserNo.of("u-001"),
      UserType.AGENT,
      new IdentityDocument(IdentityType.ID_CARD, new DocumentNumber("110101199001011234")),
      new Mobile("+8613800138000"),
      new Email("user@example.com"),
      new Telephone("010", "12345678", "100"),
      new Address("CN", "北京", "北京", "海淀", "中关村大街1号"),
      new PostalCode("100080"),
      UserStatus.ACTIVE,
      UserNo.of("creator-1"),
      LocalDateTime.of(2026, 1, 1, 10, 0),
      UserNo.of("updater-1"),
      LocalDateTime.of(2026, 1, 2, 10, 0),
      Version.of(3L)
    );
  }

  private UserAggregate minimalUser() {
    return UserAggregate.restore(
      UserNo.of("u-002"),
      UserType.TELLER,
      new IdentityDocument(IdentityType.ID_CARD, new DocumentNumber("110101199001011235")),
      new Mobile("+8613800138001"),
      null,
      null,
      null,
      null,
      UserStatus.FROZEN,
      UserNo.of("creator-2"),
      LocalDateTime.of(2026, 2, 1, 10, 0),
      UserNo.of("updater-2"),
      LocalDateTime.of(2026, 2, 2, 10, 0),
      Version.of(5L)
    );
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
    @DisplayName("应正确映射身份与基础业务字段")
    void shouldMapIdentityAndBasicFields() {
      var user = fullUser();

      var doObj = converter.toDO(user);

      assertThat(doObj.getId()).isEqualTo("u-001");
      assertThat(doObj.getUserType()).isEqualTo("AGENT");
      assertThat(doObj.getIdentityType()).isEqualTo("ID_CARD");
      assertThat(doObj.getIdentityNumber()).isEqualTo("110101199001011234");
      assertThat(doObj.getMobile()).isEqualTo("+8613800138000");
      assertThat(doObj.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("应正确映射联系方式与地址字段")
    void shouldMapContactFields() {
      var user = fullUser();

      var doObj = converter.toDO(user);

      assertThat(doObj.getEmail()).isEqualTo("user@example.com");
      assertThat(doObj.getTelephoneAreaCode()).isEqualTo("010");
      assertThat(doObj.getTelephoneNumber()).isEqualTo("12345678");
      assertThat(doObj.getTelephoneExtension()).isEqualTo("100");
      assertThat(doObj.getAddressCountry()).isEqualTo("CN");
      assertThat(doObj.getAddressProvince()).isEqualTo("北京");
      assertThat(doObj.getAddressCity()).isEqualTo("北京");
      assertThat(doObj.getAddressDistrict()).isEqualTo("海淀");
      assertThat(doObj.getAddressDetail()).isEqualTo("中关村大街1号");
      assertThat(doObj.getPostalCode()).isEqualTo("100080");
    }

    @Test
    @DisplayName("应正确映射基类字段")
    void shouldMapBaseFields() {
      var user = fullUser();

      var doObj = converter.toDO(user);

      assertThat(doObj.getCreatedBy()).isEqualTo("creator-1");
      assertThat(doObj.getUpdatedBy()).isEqualTo("updater-1");
      assertThat(doObj.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
      assertThat(doObj.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
      assertThat(doObj.getVersion()).isEqualTo(3);
      assertThat(doObj.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("可选联系方式为 null 时 DO 对应字段应为 null")
    void shouldMapNullOptionalsToNull() {
      var user = minimalUser();

      var doObj = converter.toDO(user);

      assertThat(doObj.getEmail()).isNull();
      assertThat(doObj.getTelephoneAreaCode()).isNull();
      assertThat(doObj.getTelephoneNumber()).isNull();
      assertThat(doObj.getTelephoneExtension()).isNull();
      assertThat(doObj.getAddressCountry()).isNull();
      assertThat(doObj.getAddressDetail()).isNull();
      assertThat(doObj.getPostalCode()).isNull();
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
      var doObj = new UserDO();
      doObj.setId("u-001");
      doObj.setUserType("AGENT");
      doObj.setIdentityType("ID_CARD");
      doObj.setIdentityNumber("110101199001011234");
      doObj.setMobile("+8613800138000");
      doObj.setEmail("user@example.com");
      doObj.setTelephoneAreaCode("010");
      doObj.setTelephoneNumber("12345678");
      doObj.setTelephoneExtension("100");
      doObj.setAddressCountry("CN");
      doObj.setAddressProvince("北京");
      doObj.setAddressCity("北京");
      doObj.setAddressDistrict("海淀");
      doObj.setAddressDetail("中关村大街1号");
      doObj.setPostalCode("100080");
      doObj.setStatus("ACTIVE");
      doObj.setCreatedBy("creator-1");
      doObj.setCreateTime(LocalDateTime.of(2026, 1, 1, 10, 0));
      doObj.setUpdatedBy("updater-1");
      doObj.setUpdateTime(LocalDateTime.of(2026, 1, 2, 10, 0));
      doObj.setVersion(3);

      var user = converter.toDomain(doObj);

      assertThat(user.id()).isEqualTo(UserNo.of("u-001"));
      assertThat(user.getUserType()).isEqualTo(UserType.AGENT);
      assertThat(user.getIdentityDocument().type()).isEqualTo(IdentityType.ID_CARD);
      assertThat(user.getIdentityDocument().number().value()).isEqualTo("110101199001011234");
      assertThat(user.getMobile().value()).isEqualTo("+8613800138000");
      assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
      assertThat(user.getEmail().value()).isEqualTo("user@example.com");
      assertThat(user.version().value()).isEqualTo(3L);
    }
  }

  @Nested
  @DisplayName("往返一致性")
  class RoundTripTest {

    @Test
    @DisplayName("完整用户应可完整往返")
    void shouldRoundTripFullUser() {
      var original = fullUser();

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.id()).isEqualTo(UserNo.of("u-001"));
      assertThat(roundTripped.getUserType()).isEqualTo(UserType.AGENT);
      assertThat(roundTripped.getIdentityDocument().type()).isEqualTo(IdentityType.ID_CARD);
      assertThat(roundTripped.getIdentityDocument().number().value()).isEqualTo("110101199001011234");
      assertThat(roundTripped.getMobile().value()).isEqualTo("+8613800138000");
      assertThat(roundTripped.getEmail().value()).isEqualTo("user@example.com");
      assertThat(roundTripped.getTelephone().areaCode()).isEqualTo("010");
      assertThat(roundTripped.getTelephone().number()).isEqualTo("12345678");
      assertThat(roundTripped.getTelephone().extension()).isEqualTo("100");
      assertThat(roundTripped.getAddress().country()).isEqualTo("CN");
      assertThat(roundTripped.getAddress().province()).isEqualTo("北京");
      assertThat(roundTripped.getAddress().city()).isEqualTo("北京");
      assertThat(roundTripped.getAddress().district()).isEqualTo("海淀");
      assertThat(roundTripped.getAddress().detail()).isEqualTo("中关村大街1号");
      assertThat(roundTripped.getPostalCode().value()).isEqualTo("100080");
      assertThat(roundTripped.getStatus()).isEqualTo(UserStatus.ACTIVE);
      assertThat(roundTripped.createdBy()).isEqualTo(UserNo.of("creator-1"));
      assertThat(roundTripped.updatedBy()).isEqualTo(UserNo.of("updater-1"));
      assertThat(roundTripped.version().value()).isEqualTo(3L);
    }

    @Test
    @DisplayName("仅必填字段用户应可往返且可选字段保持 null")
    void shouldRoundTripMinimalUser() {
      var original = minimalUser();

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.id()).isEqualTo(UserNo.of("u-002"));
      assertThat(roundTripped.getUserType()).isEqualTo(UserType.TELLER);
      assertThat(roundTripped.getStatus()).isEqualTo(UserStatus.FROZEN);
      assertThat(roundTripped.getEmail()).isNull();
      assertThat(roundTripped.getTelephone()).isNull();
      assertThat(roundTripped.getAddress()).isNull();
      assertThat(roundTripped.getPostalCode()).isNull();
      assertThat(roundTripped.version().value()).isEqualTo(5L);
    }
  }
}
