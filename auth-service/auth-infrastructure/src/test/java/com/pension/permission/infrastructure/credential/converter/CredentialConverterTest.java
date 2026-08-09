package com.pension.permission.infrastructure.credential.converter;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.valueobject.ValidityPeriod;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pension.permission.domain.credential.aggregate.PasswordCredential;
import com.pension.permission.domain.credential.aggregate.UKeyCredential;
import com.pension.permission.domain.credential.enumeration.CredentialStatus;
import com.pension.permission.domain.credential.enumeration.CredentialType;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.CustomerCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.PlanCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.types.CredentialId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CredentialConverter 转换器测试")
class CredentialConverterTest {

  private CredentialConverter converter;

  @BeforeEach
  void setUp() throws Exception {
    converter = new CredentialConverterImpl();
    Field field = CredentialConverter.class.getDeclaredField("objectMapper");
    field.setAccessible(true);
    field.set(converter, new ObjectMapper());
  }

  private PasswordCredential samplePasswordCredential(CredentialOwner owner) {
    return PasswordCredential.reconstitute(
      new CredentialId("c-pwd-001"),
      owner,
      Set.of(AnnuityChannel.NETAPP, AnnuityChannel.BANK_BRANCH),
      CredentialStatus.ACTIVE,
      ValidityPeriod.infinite(),
      UserNo.of("creator-1"),
      LocalDateTime.of(2026, 1, 1, 10, 0),
      UserNo.of("updater-1"),
      LocalDateTime.of(2026, 1, 2, 10, 0),
      Version.of(2L),
      UserNo.of("user-1"),
      "hashed-pwd-001");
  }

  private UKeyCredential sampleUKeyCredential(CredentialOwner owner) {
    return UKeyCredential.reconstitute(
      new CredentialId("c-ukey-001"),
      owner,
      Set.of(AnnuityChannel.BANK_BRANCH),
      CredentialStatus.ACTIVE,
      ValidityPeriod.between(
        LocalDateTime.of(2026, 1, 1, 0, 0),
        LocalDateTime.of(2026, 12, 31, 23, 59)),
      UserNo.of("creator-1"),
      LocalDateTime.of(2026, 1, 1, 10, 0),
      UserNo.of("updater-1"),
      LocalDateTime.of(2026, 1, 2, 10, 0),
      Version.of(1L),
      "key-serial-001");
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
    @DisplayName("PasswordCredential 应正确映射基础字段")
    void shouldMapPasswordCredentialBasicFields() {
      var credential = samplePasswordCredential(new UserCredentialOwner(UserNo.of("user-1")));

      var doObj = converter.toDO(credential);

      assertThat(doObj.getId()).isEqualTo("c-pwd-001");
      assertThat(doObj.getCredentialType()).isEqualTo("PASSWORD");
      assertThat(doObj.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("PasswordCredential 应映射子类专属字段")
    void shouldMapPasswordCredentialSpecificFields() {
      var credential = samplePasswordCredential(new UserCredentialOwner(UserNo.of("user-1")));

      var doObj = converter.toDO(credential);

      assertThat(doObj.getUserNo()).isEqualTo("user-1");
      assertThat(doObj.getPasswordHash()).isEqualTo("hashed-pwd-001");
      assertThat(doObj.getKeySerial()).isNull();
    }

    @Test
    @DisplayName("UKeyCredential 应正确映射基础字段")
    void shouldMapUKeyCredentialBasicFields() {
      var credential = sampleUKeyCredential(new UserCredentialOwner(UserNo.of("user-1")));

      var doObj = converter.toDO(credential);

      assertThat(doObj.getId()).isEqualTo("c-ukey-001");
      assertThat(doObj.getCredentialType()).isEqualTo("U_KEY");
      assertThat(doObj.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("UKeyCredential 应映射子类专属字段")
    void shouldMapUKeyCredentialSpecificFields() {
      var credential = sampleUKeyCredential(new UserCredentialOwner(UserNo.of("user-1")));

      var doObj = converter.toDO(credential);

      assertThat(doObj.getKeySerial()).isEqualTo("key-serial-001");
      assertThat(doObj.getUserNo()).isNull();
      assertThat(doObj.getPasswordHash()).isNull();
    }

    @Test
    @DisplayName("应正确映射基类字段")
    void shouldMapBaseFields() {
      var credential = samplePasswordCredential(new UserCredentialOwner(UserNo.of("user-1")));

      var doObj = converter.toDO(credential);

      assertThat(doObj.getCreatedBy()).isEqualTo("creator-1");
      assertThat(doObj.getUpdatedBy()).isEqualTo("updater-1");
      assertThat(doObj.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
      assertThat(doObj.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
      assertThat(doObj.getVersion()).isEqualTo(2);
      assertThat(doObj.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("应序列化适用渠道集合为 JSON")
    void shouldSerializeApplicableChannels() {
      var credential = samplePasswordCredential(new UserCredentialOwner(UserNo.of("user-1")));

      var doObj = converter.toDO(credential);

      assertThat(doObj.getApplicableChannels()).contains("NETAPP");
      assertThat(doObj.getApplicableChannels()).contains("BANK_BRANCH");
    }

    @Test
    @DisplayName("应正确映射 ValidityPeriod")
    void shouldMapValidityPeriod() {
      var credential = sampleUKeyCredential(new UserCredentialOwner(UserNo.of("user-1")));

      var doObj = converter.toDO(credential);

      assertThat(doObj.getValidityStart()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
      assertThat(doObj.getValidityEnd()).isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59));
    }
  }

  @Nested
  @DisplayName("toDO: CredentialOwner 多态映射")
  class OwnerMappingTest {

    @Test
    @DisplayName("UserCredentialOwner 应映射 ownerType=UserCredentialOwner")
    void shouldMapUserCredentialOwner() {
      var credential = samplePasswordCredential(new UserCredentialOwner(UserNo.of("user-1")));

      var doObj = converter.toDO(credential);

      assertThat(doObj.getOwnerType()).isEqualTo("UserCredentialOwner");
      assertThat(doObj.getOwnerId()).isEqualTo("user-1");
    }

    @Test
    @DisplayName("CustomerCredentialOwner 应映射 ownerType=CustomerCredentialOwner")
    void shouldMapCustomerCredentialOwner() {
      var credential = samplePasswordCredential(new CustomerCredentialOwner(CustomerNo.of("cust-1")));

      var doObj = converter.toDO(credential);

      assertThat(doObj.getOwnerType()).isEqualTo("CustomerCredentialOwner");
      assertThat(doObj.getOwnerId()).isEqualTo("cust-1");
    }

    @Test
    @DisplayName("PlanCredentialOwner 应映射 ownerType=PlanCredentialOwner")
    void shouldMapPlanCredentialOwner() {
      var credential = samplePasswordCredential(new PlanCredentialOwner(PlanNo.of("PLAN-001")));

      var doObj = converter.toDO(credential);

      assertThat(doObj.getOwnerType()).isEqualTo("PlanCredentialOwner");
      assertThat(doObj.getOwnerId()).isEqualTo("PLAN-001");
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
    @DisplayName("应正确往返 PasswordCredential")
    void shouldRoundTripPasswordCredential() {
      var original = samplePasswordCredential(new UserCredentialOwner(UserNo.of("user-1")));

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped).isInstanceOf(PasswordCredential.class);
      assertThat(roundTripped.id()).isEqualTo(original.id());
      assertThat(roundTripped.type()).isEqualTo(CredentialType.PASSWORD);
      assertThat(roundTripped.status()).isEqualTo(CredentialStatus.ACTIVE);
      var pwdCredential = (PasswordCredential) roundTripped;
      assertThat(pwdCredential.userNo()).isEqualTo(UserNo.of("user-1"));
      assertThat(pwdCredential.passwordHash()).isEqualTo("hashed-pwd-001");
    }

    @Test
    @DisplayName("应正确往返 UKeyCredential")
    void shouldRoundTripUKeyCredential() {
      var original = sampleUKeyCredential(new UserCredentialOwner(UserNo.of("user-1")));

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped).isInstanceOf(UKeyCredential.class);
      assertThat(roundTripped.id()).isEqualTo(original.id());
      assertThat(roundTripped.type()).isEqualTo(CredentialType.U_KEY);
      var ukeyCredential = (UKeyCredential) roundTripped;
      assertThat(ukeyCredential.keySerial()).isEqualTo("key-serial-001");
    }

    @Test
    @DisplayName("应正确往返 UserCredentialOwner")
    void shouldRoundTripUserCredentialOwner() {
      var original = samplePasswordCredential(new UserCredentialOwner(UserNo.of("user-1")));

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.owner()).isInstanceOf(UserCredentialOwner.class);
      assertThat(((UserCredentialOwner) roundTripped.owner()).userNo()).isEqualTo(UserNo.of("user-1"));
    }

    @Test
    @DisplayName("应正确往返 CustomerCredentialOwner")
    void shouldRoundTripCustomerCredentialOwner() {
      var original = samplePasswordCredential(new CustomerCredentialOwner(CustomerNo.of("cust-1")));

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.owner()).isInstanceOf(CustomerCredentialOwner.class);
      assertThat(((CustomerCredentialOwner) roundTripped.owner()).customerNo()).isEqualTo(CustomerNo.of("cust-1"));
    }

    @Test
    @DisplayName("应正确往返 PlanCredentialOwner")
    void shouldRoundTripPlanCredentialOwner() {
      var original = samplePasswordCredential(new PlanCredentialOwner(PlanNo.of("PLAN-001")));

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.owner()).isInstanceOf(PlanCredentialOwner.class);
      assertThat(((PlanCredentialOwner) roundTripped.owner()).planNo()).isEqualTo(PlanNo.of("PLAN-001"));
    }

    @Test
    @DisplayName("应正确往返适用渠道集合")
    void shouldRoundTripApplicableChannels() {
      var original = samplePasswordCredential(new UserCredentialOwner(UserNo.of("user-1")));

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.applicableChannels()).containsExactlyInAnyOrder(
        AnnuityChannel.NETAPP, AnnuityChannel.BANK_BRANCH);
    }

    @Test
    @DisplayName("应正确往返 ValidityPeriod")
    void shouldRoundTripValidityPeriod() {
      var original = sampleUKeyCredential(new UserCredentialOwner(UserNo.of("user-1")));

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.validityPeriod().start()).isEqualTo(LocalDateTime.of(2026, 1, 1, 0, 0));
      assertThat(roundTripped.validityPeriod().end()).isEqualTo(LocalDateTime.of(2026, 12, 31, 23, 59));
    }
  }
}
