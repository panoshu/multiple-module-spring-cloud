package com.pension.permission.infrastructure.channel.converter;

import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.channel.aggregate.Session;
import com.pension.permission.domain.channel.enumeration.SessionStatus;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.infrastructure.channel.entity.SessionDO;
import com.pension.permission.types.SecondaryAuthSessionId;
import com.pension.permission.types.SessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SessionConverter 转换器测试")
class SessionConverterTest {

  private SessionConverter converter;

  @BeforeEach
  void setUp() {
    converter = new SessionConverterImpl();
  }

  /**
   * 构造一个完整的 Session 样本（含二次授权绑定与计划选择）.
   */
  private Session sampleSession() {
    return Session.reconstitute(
      new SessionId("sess-001"),
      UserNo.of("creator-1"),
      UserNo.of("updater-1"),
      LocalDateTime.of(2026, 1, 1, 10, 0),
      LocalDateTime.of(2026, 1, 2, 10, 0),
      Version.of(3L),
      UserNo.of("user-001"),
      AnnuityChannel.BANK_BRANCH,
      new EffectiveIdentity(
        UserNo.of("approver-001"),
        UserNo.of("teller-001"),
        true),
      PlanNo.of("PLAN-001"),
      LocalDateTime.of(2026, 1, 1, 12, 0),
      SessionStatus.ACTIVE,
      new SecondaryAuthSessionId("sec-001")
    );
  }

  /**
   * 构造一个无二次授权、无计划选择的 Session 样本（直接身份）.
   */
  private Session directIdentitySession() {
    return Session.reconstitute(
      new SessionId("sess-002"),
      UserNo.of("creator-2"),
      UserNo.of("updater-2"),
      LocalDateTime.of(2026, 2, 1, 10, 0),
      LocalDateTime.of(2026, 2, 2, 10, 0),
      Version.of(5L),
      UserNo.of("user-002"),
      AnnuityChannel.NETAPP,
      EffectiveIdentity.direct(UserNo.of("user-002")),
      null,
      LocalDateTime.of(2026, 2, 1, 12, 0),
      SessionStatus.ACTIVE,
      null
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
    @DisplayName("应正确映射基础字段")
    void shouldMapBasicFields() {
      var session = sampleSession();

      var doObj = converter.toDO(session);

      assertThat(doObj.getId()).isEqualTo("sess-001");
      assertThat(doObj.getPrimaryAccountId()).isEqualTo("user-001");
      assertThat(doObj.getChannel()).isEqualTo("BANK_BRANCH");
      assertThat(doObj.getStatus()).isEqualTo("ACTIVE");
      assertThat(doObj.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 0));
      assertThat(doObj.getSecondaryAuthSessionId()).isEqualTo("sec-001");
      assertThat(doObj.getSelectedPlanId()).isEqualTo("PLAN-001");
    }

    @Test
    @DisplayName("应正确映射基类字段")
    void shouldMapBaseFields() {
      var session = sampleSession();

      var doObj = converter.toDO(session);

      assertThat(doObj.getCreatedBy()).isEqualTo("creator-1");
      assertThat(doObj.getUpdatedBy()).isEqualTo("updater-1");
      assertThat(doObj.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
      assertThat(doObj.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
      assertThat(doObj.getVersion()).isEqualTo(3);
      assertThat(doObj.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("应正确拆解 EffectiveIdentity 到三列")
    void shouldDecomposeEffectiveIdentity() {
      var session = sampleSession();

      var doObj = converter.toDO(session);

      assertThat(doObj.getEffectiveIdentityId()).isEqualTo("approver-001");
      assertThat(doObj.getEffectiveIdentityActing()).isEqualTo("teller-001");
      assertThat(doObj.getEffectiveViaSecondary()).isTrue();
    }

    @Test
    @DisplayName("direct 身份应拆解为相同的 identityId 与 acting，且 viaSecondary=false")
    void shouldDecomposeDirectIdentity() {
      var session = directIdentitySession();

      var doObj = converter.toDO(session);

      assertThat(doObj.getEffectiveIdentityId()).isEqualTo("user-002");
      assertThat(doObj.getEffectiveIdentityActing()).isEqualTo("user-002");
      assertThat(doObj.getEffectiveViaSecondary()).isFalse();
      assertThat(doObj.getSecondaryAuthSessionId()).isNull();
      assertThat(doObj.getSelectedPlanId()).isNull();
    }

    @Test
    @DisplayName("viaSecondary=false 的身份应拆解 viaSecondary 列为 false")
    void shouldDecomposeNonSecondaryIdentity() {
      var session = Session.reconstitute(
        new SessionId("sess-003"),
        UserNo.of("creator-3"),
        UserNo.of("updater-3"),
        LocalDateTime.of(2026, 3, 1, 10, 0),
        LocalDateTime.of(2026, 3, 2, 10, 0),
        Version.of(1L),
        UserNo.of("user-003"),
        AnnuityChannel.WECHAT,
        new EffectiveIdentity(UserNo.of("user-003"), UserNo.of("teller-003"), false),
        null,
        LocalDateTime.of(2026, 3, 1, 12, 0),
        SessionStatus.ACTIVE,
        null
      );

      var doObj = converter.toDO(session);

      assertThat(doObj.getEffectiveIdentityId()).isEqualTo("user-003");
      assertThat(doObj.getEffectiveIdentityActing()).isEqualTo("teller-003");
      assertThat(doObj.getEffectiveViaSecondary()).isFalse();
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
    @DisplayName("应正确映射基础字段")
    void shouldMapBasicFields() {
      var doObj = new SessionDO();
      doObj.setId("sess-100");
      doObj.setPrimaryAccountId("user-100");
      doObj.setChannel("NETAPP");
      doObj.setStatus("CLOSED");
      doObj.setExpiresAt(LocalDateTime.of(2026, 5, 1, 12, 0));
      doObj.setSecondaryAuthSessionId("sec-100");
      doObj.setSelectedPlanId("PLAN-100");
      doObj.setCreatedBy("creator-100");
      doObj.setUpdatedBy("updater-100");
      doObj.setCreateTime(LocalDateTime.of(2026, 5, 1, 10, 0));
      doObj.setUpdateTime(LocalDateTime.of(2026, 5, 2, 10, 0));
      doObj.setVersion(7);
      doObj.setEffectiveIdentityId("approver-100");
      doObj.setEffectiveIdentityActing("teller-100");
      doObj.setEffectiveViaSecondary(true);

      var session = converter.toDomain(doObj);

      assertThat(session.id().value()).isEqualTo("sess-100");
      assertThat(session.primaryAccountId().value()).isEqualTo("user-100");
      assertThat(session.channel()).isEqualTo(AnnuityChannel.NETAPP);
      assertThat(session.status()).isEqualTo(SessionStatus.CLOSED);
      assertThat(session.expiresAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 12, 0));
      assertThat(session.secondaryAuthSessionId().value()).isEqualTo("sec-100");
      assertThat(session.selectedPlanId().value()).isEqualTo("PLAN-100");
    }

    @Test
    @DisplayName("应正确映射基类字段")
    void shouldMapBaseFields() {
      var doObj = new SessionDO();
      doObj.setId("sess-100");
      doObj.setPrimaryAccountId("user-100");
      doObj.setChannel("NETAPP");
      doObj.setStatus("ACTIVE");
      doObj.setExpiresAt(LocalDateTime.of(2026, 5, 1, 12, 0));
      doObj.setCreatedBy("creator-100");
      doObj.setUpdatedBy("updater-100");
      doObj.setCreateTime(LocalDateTime.of(2026, 5, 1, 10, 0));
      doObj.setUpdateTime(LocalDateTime.of(2026, 5, 2, 10, 0));
      doObj.setVersion(7);
      doObj.setEffectiveIdentityId("user-100");
      doObj.setEffectiveIdentityActing("user-100");
      doObj.setEffectiveViaSecondary(false);

      var session = converter.toDomain(doObj);

      assertThat(session.createdBy().value()).isEqualTo("creator-100");
      assertThat(session.updatedBy().value()).isEqualTo("updater-100");
      assertThat(session.createdAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 10, 0));
      assertThat(session.updatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 2, 10, 0));
      assertThat(session.version().value()).isEqualTo(7L);
    }

    @Test
    @DisplayName("应正确合并 EffectiveIdentity 三列到值对象")
    void shouldComposeEffectiveIdentity() {
      var doObj = new SessionDO();
      doObj.setId("sess-100");
      doObj.setPrimaryAccountId("user-100");
      doObj.setChannel("BANK_BRANCH");
      doObj.setStatus("ACTIVE");
      doObj.setExpiresAt(LocalDateTime.of(2026, 5, 1, 12, 0));
      doObj.setEffectiveIdentityId("approver-100");
      doObj.setEffectiveIdentityActing("teller-100");
      doObj.setEffectiveViaSecondary(true);

      var session = converter.toDomain(doObj);

      assertThat(session.effectiveIdentity()).isNotNull();
      assertThat(session.effectiveIdentity().identityAccountId().value()).isEqualTo("approver-100");
      assertThat(session.effectiveIdentity().actingAccountId().value()).isEqualTo("teller-100");
      assertThat(session.effectiveIdentity().viaSecondaryAuth()).isTrue();
    }

    @Test
    @DisplayName("selectedPlanId 和 secondaryAuthSessionId 为 null 时应正确解析")
    void shouldResolveNullOptionalFields() {
      var doObj = new SessionDO();
      doObj.setId("sess-100");
      doObj.setPrimaryAccountId("user-100");
      doObj.setChannel("NETAPP");
      doObj.setStatus("ACTIVE");
      doObj.setExpiresAt(LocalDateTime.of(2026, 5, 1, 12, 0));
      doObj.setEffectiveIdentityId("user-100");
      doObj.setEffectiveIdentityActing("user-100");
      doObj.setEffectiveViaSecondary(false);
      doObj.setSelectedPlanId(null);
      doObj.setSecondaryAuthSessionId(null);

      var session = converter.toDomain(doObj);

      assertThat(session.selectedPlanId()).isNull();
      assertThat(session.secondaryAuthSessionId()).isNull();
    }

    @Test
    @DisplayName("effectiveViaSecondary 为 null 应解析为 false")
    void shouldResolveNullViaSecondaryAsFalse() {
      var doObj = new SessionDO();
      doObj.setId("sess-100");
      doObj.setPrimaryAccountId("user-100");
      doObj.setChannel("NETAPP");
      doObj.setStatus("ACTIVE");
      doObj.setExpiresAt(LocalDateTime.of(2026, 5, 1, 12, 0));
      doObj.setEffectiveIdentityId("user-100");
      doObj.setEffectiveIdentityActing("user-100");
      doObj.setEffectiveViaSecondary(null);

      var session = converter.toDomain(doObj);

      assertThat(session.effectiveIdentity().viaSecondaryAuth()).isFalse();
    }
  }

  @Nested
  @DisplayName("往返一致性: toDomain(toDO(session))")
  class RoundTripTest {

    @Test
    @DisplayName("二次授权会话应能完整往返")
    void shouldRoundTripSecondaryAuthSession() {
      var original = sampleSession();

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.id().value()).isEqualTo("sess-001");
      assertThat(roundTripped.primaryAccountId().value()).isEqualTo("user-001");
      assertThat(roundTripped.channel()).isEqualTo(AnnuityChannel.BANK_BRANCH);
      assertThat(roundTripped.status()).isEqualTo(SessionStatus.ACTIVE);
      assertThat(roundTripped.expiresAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 12, 0));
      assertThat(roundTripped.selectedPlanId().value()).isEqualTo("PLAN-001");
      assertThat(roundTripped.secondaryAuthSessionId().value()).isEqualTo("sec-001");
      assertThat(roundTripped.createdBy().value()).isEqualTo("creator-1");
      assertThat(roundTripped.updatedBy().value()).isEqualTo("updater-1");
      assertThat(roundTripped.version().value()).isEqualTo(3L);
    }

    @Test
    @DisplayName("direct 身份应能完整往返")
    void shouldRoundTripDirectIdentitySession() {
      var original = directIdentitySession();

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.id().value()).isEqualTo("sess-002");
      assertThat(roundTripped.channel()).isEqualTo(AnnuityChannel.NETAPP);
      assertThat(roundTripped.secondaryAuthSessionId()).isNull();
      assertThat(roundTripped.selectedPlanId()).isNull();
    }

    @Test
    @DisplayName("EffectiveIdentity 应能双向往返（拆解 → 合并）")
    void shouldRoundTripEffectiveIdentity() {
      var original = sampleSession();

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.effectiveIdentity()).isNotNull();
      assertThat(roundTripped.effectiveIdentity().identityAccountId())
        .isEqualTo(original.effectiveIdentity().identityAccountId());
      assertThat(roundTripped.effectiveIdentity().actingAccountId())
        .isEqualTo(original.effectiveIdentity().actingAccountId());
      assertThat(roundTripped.effectiveIdentity().viaSecondaryAuth())
        .isEqualTo(original.effectiveIdentity().viaSecondaryAuth());
    }

    @Test
    @DisplayName("direct EffectiveIdentity 往返后保持 identityId == actingAccountId")
    void shouldRoundTripDirectIdentity() {
      var original = directIdentitySession();

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.effectiveIdentity().identityAccountId())
        .isEqualTo(roundTripped.effectiveIdentity().actingAccountId());
      assertThat(roundTripped.effectiveIdentity().viaSecondaryAuth()).isFalse();
    }
  }
}
