package com.pension.permission.infrastructure.channel.converter;

import com.example.shared.contactinfo.Mobile;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pension.permission.domain.authorization.valueobject.ActionCode;
import com.pension.permission.domain.authorization.valueobject.BusinessCode;
import com.pension.permission.domain.authorization.valueobject.Permission;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession;
import com.pension.permission.domain.channel.aggregate.SecondaryAuthSession.ReconstituteSnapshot;
import com.pension.permission.domain.channel.enumeration.SecondaryAuthStatus;
import com.pension.permission.domain.channel.valueobject.EffectiveIdentity;
import com.pension.permission.domain.channel.valueobject.PermissionSnapshot;
import com.pension.permission.domain.channel.valueobject.VerificationCode;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.CustomerCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.PlanCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.infrastructure.channel.entity.SecondaryAuthSessionDO;
import com.pension.permission.types.SecondaryAuthSessionId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecondaryAuthSessionConverter 转换器测试")
class SecondaryAuthSessionConverterTest {

  private SecondaryAuthSessionConverter converter;

  @BeforeEach
  void setUp() throws Exception {
    converter = new SecondaryAuthSessionConverterImpl();
    Field field = SecondaryAuthSessionConverter.class.getDeclaredField("objectMapper");
    field.setAccessible(true);
    field.set(converter, new ObjectMapper());
  }

  /**
   * 构造 AUTHORIZED 状态的完整样本（含 effectiveIdentity 和 permissionSnapshot，无 verificationCode）.
   */
  private SecondaryAuthSession authorizedSession(CredentialOwner owner) {
    return SecondaryAuthSession.reconstitute(
      new ReconstituteSnapshot(
        new SecondaryAuthSessionId("sec-001"),
        UserNo.of("creator-1"),
        UserNo.of("updater-1"),
        LocalDateTime.of(2026, 1, 1, 10, 0),
        LocalDateTime.of(2026, 1, 2, 10, 0),
        Version.of(3L),

        UserNo.of("teller-001"),
        UserNo.of("approver-001"),
        owner,
        new Mobile("+8613800138000"),
        PlanNo.of("PLAN-001"),

        null,
        new EffectiveIdentity(
          UserNo.of("approver-001"),
          UserNo.of("teller-001"),
          true),
        new PermissionSnapshot(
          Set.of(new Permission(new BusinessCode("BIZ-001"), new ActionCode("ACT-VIEW"))),
          LocalDateTime.of(2026, 1, 1, 10, 0),
          LocalDateTime.of(2026, 1, 1, 22, 0)),

        SecondaryAuthStatus.AUTHORIZED,
        LocalDateTime.of(2026, 1, 1, 10, 0),
        LocalDateTime.of(2026, 1, 1, 10, 5),
        LocalDateTime.of(2026, 1, 1, 10, 3),
        LocalDateTime.of(2026, 1, 1, 22, 0),
        null
      )
    );
  }

  /**
   * 构造 PENDING 状态样本（含 verificationCode，无 effectiveIdentity 和 permissionSnapshot）.
   */
  private SecondaryAuthSession pendingSession(CredentialOwner owner) {
    return SecondaryAuthSession.reconstitute(
      new ReconstituteSnapshot(
        new SecondaryAuthSessionId("sec-002"),
        UserNo.of("creator-2"),
        UserNo.of("updater-2"),
        LocalDateTime.of(2026, 2, 1, 10, 0),
        LocalDateTime.of(2026, 2, 2, 10, 0),
        Version.of(1L),

        UserNo.of("teller-002"),
        null,
        owner,
        new Mobile("+8613800138001"),
        PlanNo.of("PLAN-002"),

        new VerificationCode(
          "hashed-code-001",
          LocalDateTime.of(2026, 2, 1, 10, 0),
          LocalDateTime.of(2026, 2, 1, 10, 5),
          3),
        null,
        null,

        SecondaryAuthStatus.PENDING,
        LocalDateTime.of(2026, 2, 1, 10, 0),
        LocalDateTime.of(2026, 2, 1, 10, 5),
        null,
        LocalDateTime.of(2026, 2, 1, 22, 0),
        null
      )
    );
  }

  private UserCredentialOwner userOwner() {
    return new UserCredentialOwner(UserNo.of("user-001"));
  }

  private CustomerCredentialOwner customerOwner() {
    return new CustomerCredentialOwner(CustomerNo.of("CUST-001"));
  }

  private PlanCredentialOwner planOwner() {
    return new PlanCredentialOwner(PlanNo.of("PLAN-OWNER-001"));
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
      var session = authorizedSession(userOwner());

      var doObj = converter.toDO(session);

      assertThat(doObj.getId()).isEqualTo("sec-001");
      assertThat(doObj.getTellerAccountId()).isEqualTo("teller-001");
      assertThat(doObj.getApproverAccountId()).isEqualTo("approver-001");
      assertThat(doObj.getApproverMobile()).isEqualTo("+8613800138000");
      assertThat(doObj.getPlanId()).isEqualTo("PLAN-001");
      assertThat(doObj.getStatus()).isEqualTo("AUTHORIZED");
      assertThat(doObj.getInitiatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
      assertThat(doObj.getPendingExpiresAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 5));
      assertThat(doObj.getAuthorizedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 3));
      assertThat(doObj.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 22, 0));
      assertThat(doObj.getRevokeReason()).isNull();
    }

    @Test
    @DisplayName("应正确映射基类字段")
    void shouldMapBaseFields() {
      var session = authorizedSession(userOwner());

      var doObj = converter.toDO(session);

      assertThat(doObj.getCreatedBy()).isEqualTo("creator-1");
      assertThat(doObj.getUpdatedBy()).isEqualTo("updater-1");
      assertThat(doObj.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
      assertThat(doObj.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 1, 2, 10, 0));
      assertThat(doObj.getVersion()).isEqualTo(3);
      assertThat(doObj.getDeleted()).isFalse();
    }

    @Test
    @DisplayName("应正确拆解 UserCredentialOwner 到 type+id 两列")
    void shouldDecomposeUserCredentialOwner() {
      var session = authorizedSession(userOwner());

      var doObj = converter.toDO(session);

      assertThat(doObj.getCredentialOwnerType()).isEqualTo("USER");
      assertThat(doObj.getCredentialOwnerId()).isEqualTo("user-001");
    }

    @Test
    @DisplayName("应正确拆解 CustomerCredentialOwner 到 type+id 两列")
    void shouldDecomposeCustomerCredentialOwner() {
      var session = authorizedSession(customerOwner());

      var doObj = converter.toDO(session);

      assertThat(doObj.getCredentialOwnerType()).isEqualTo("CUSTOMER");
      assertThat(doObj.getCredentialOwnerId()).isEqualTo("CUST-001");
    }

    @Test
    @DisplayName("应正确拆解 PlanCredentialOwner 到 type+id 两列")
    void shouldDecomposePlanCredentialOwner() {
      var session = authorizedSession(planOwner());

      var doObj = converter.toDO(session);

      assertThat(doObj.getCredentialOwnerType()).isEqualTo("PLAN");
      assertThat(doObj.getCredentialOwnerId()).isEqualTo("PLAN-OWNER-001");
    }

    @Test
    @DisplayName("应正确拆解 VerificationCode 到四列")
    void shouldDecomposeVerificationCode() {
      var session = pendingSession(userOwner());

      var doObj = converter.toDO(session);

      assertThat(doObj.getVerificationCodeHash()).isEqualTo("hashed-code-001");
      assertThat(doObj.getVerificationSentAt()).isEqualTo(LocalDateTime.of(2026, 2, 1, 10, 0));
      assertThat(doObj.getVerificationExpiresAt()).isEqualTo(LocalDateTime.of(2026, 2, 1, 10, 5));
      assertThat(doObj.getVerificationRemaining()).isEqualTo(3);
    }

    @Test
    @DisplayName("null VerificationCode 应拆解为四个 null 列")
    void shouldDecomposeNullVerificationCode() {
      var session = authorizedSession(userOwner());

      var doObj = converter.toDO(session);

      assertThat(doObj.getVerificationCodeHash()).isNull();
      assertThat(doObj.getVerificationSentAt()).isNull();
      assertThat(doObj.getVerificationExpiresAt()).isNull();
      assertThat(doObj.getVerificationRemaining()).isNull();
    }

    @Test
    @DisplayName("应正确拆解 EffectiveIdentity 到三列")
    void shouldDecomposeEffectiveIdentity() {
      var session = authorizedSession(userOwner());

      var doObj = converter.toDO(session);

      assertThat(doObj.getEffectiveIdentityId()).isEqualTo("approver-001");
      assertThat(doObj.getEffectiveIdentityActing()).isEqualTo("teller-001");
      assertThat(doObj.getEffectiveViaSecondary()).isTrue();
    }

    @Test
    @DisplayName("应正确序列化 PermissionSnapshot.permissions 为 JSON")
    void shouldSerializePermissionSnapshot() {
      var session = authorizedSession(userOwner());

      var doObj = converter.toDO(session);

      assertThat(doObj.getSnapshotPermissions()).contains("BIZ-001");
      assertThat(doObj.getSnapshotPermissions()).contains("ACT-VIEW");
      assertThat(doObj.getSnapshotFrozenAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
      assertThat(doObj.getSnapshotExpiresAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 22, 0));
    }

    @Test
    @DisplayName("null PermissionSnapshot 应序列化为 null JSON")
    void shouldSerializeNullPermissionSnapshot() {
      var session = pendingSession(userOwner());

      var doObj = converter.toDO(session);

      assertThat(doObj.getSnapshotPermissions()).isNull();
      assertThat(doObj.getSnapshotFrozenAt()).isNull();
      assertThat(doObj.getSnapshotExpiresAt()).isNull();
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
      var doObj = buildAuthorizedDO();

      var session = converter.toDomain(doObj);

      assertThat(session.id().value()).isEqualTo("sec-100");
      assertThat(session.tellerAccountId().value()).isEqualTo("teller-100");
      assertThat(session.approverAccountId().value()).isEqualTo("approver-100");
      assertThat(session.approverMobile().value()).isEqualTo("+8613800138002");
      assertThat(session.planId().value()).isEqualTo("PLAN-100");
      assertThat(session.status()).isEqualTo(SecondaryAuthStatus.AUTHORIZED);
      assertThat(session.initiatedAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 10, 0));
      assertThat(session.pendingExpiresAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 10, 5));
      assertThat(session.authorizedAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 10, 3));
      assertThat(session.expiresAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 22, 0));
      assertThat(session.revokeReason()).isNull();
    }

    @Test
    @DisplayName("应正确重建 UserCredentialOwner")
    void shouldComposeUserCredentialOwner() {
      var doObj = buildAuthorizedDO();
      doObj.setCredentialOwnerType("USER");
      doObj.setCredentialOwnerId("user-100");

      var session = converter.toDomain(doObj);

      assertThat(session.credentialOwner()).isInstanceOf(UserCredentialOwner.class);
      assertThat(((UserCredentialOwner) session.credentialOwner()).userNo().value())
        .isEqualTo("user-100");
    }

    @Test
    @DisplayName("应正确重建 CustomerCredentialOwner")
    void shouldComposeCustomerCredentialOwner() {
      var doObj = buildAuthorizedDO();
      doObj.setCredentialOwnerType("CUSTOMER");
      doObj.setCredentialOwnerId("CUST-100");

      var session = converter.toDomain(doObj);

      assertThat(session.credentialOwner()).isInstanceOf(CustomerCredentialOwner.class);
      assertThat(((CustomerCredentialOwner) session.credentialOwner()).customerNo().value())
        .isEqualTo("CUST-100");
    }

    @Test
    @DisplayName("应正确重建 PlanCredentialOwner")
    void shouldComposePlanCredentialOwner() {
      var doObj = buildAuthorizedDO();
      doObj.setCredentialOwnerType("PLAN");
      doObj.setCredentialOwnerId("PLAN-OWNER-100");

      var session = converter.toDomain(doObj);

      assertThat(session.credentialOwner()).isInstanceOf(PlanCredentialOwner.class);
      assertThat(((PlanCredentialOwner) session.credentialOwner()).planNo().value())
        .isEqualTo("PLAN-OWNER-100");
    }

    @Test
    @DisplayName("应正确重建 VerificationCode")
    void shouldComposeVerificationCode() {
      var doObj = buildPendingDO();

      var session = converter.toDomain(doObj);

      assertThat(session.verificationCode()).isNotNull();
      assertThat(session.verificationCode().hashedCode()).isEqualTo("hashed-100");
      assertThat(session.verificationCode().sentAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 10, 0));
      assertThat(session.verificationCode().expiresAt()).isEqualTo(LocalDateTime.of(2026, 6, 1, 10, 5));
      assertThat(session.verificationCode().remainingAttempts()).isEqualTo(2);
    }

    @Test
    @DisplayName("null verificationCodeHash 时 VerificationCode 应为 null")
    void shouldReturnNullVerificationCodeWhenHashNull() {
      var doObj = buildAuthorizedDO();
      doObj.setVerificationCodeHash(null);

      var session = converter.toDomain(doObj);

      assertThat(session.verificationCode()).isNull();
    }

    @Test
    @DisplayName("null verificationRemaining 应解析为 0")
    void shouldResolveNullVerificationRemainingAsZero() {
      var doObj = buildPendingDO();
      doObj.setVerificationRemaining(null);

      var session = converter.toDomain(doObj);

      assertThat(session.verificationCode().remainingAttempts()).isEqualTo(0);
    }

    @Test
    @DisplayName("应正确合并 EffectiveIdentity 三列到值对象")
    void shouldComposeEffectiveIdentity() {
      var doObj = buildAuthorizedDO();

      var session = converter.toDomain(doObj);

      assertThat(session.effectiveIdentity()).isNotNull();
      assertThat(session.effectiveIdentity().identityAccountId().value()).isEqualTo("approver-100");
      assertThat(session.effectiveIdentity().actingAccountId().value()).isEqualTo("teller-100");
      assertThat(session.effectiveIdentity().viaSecondaryAuth()).isTrue();
    }

    @Test
    @DisplayName("应正确反序列化 PermissionSnapshot JSON")
    void shouldComposePermissionSnapshot() {
      var doObj = buildAuthorizedDO();

      var session = converter.toDomain(doObj);

      assertThat(session.permissionSnapshot()).isNotNull();
      assertThat(session.permissionSnapshot().frozenAt())
        .isEqualTo(LocalDateTime.of(2026, 5, 1, 10, 0));
      assertThat(session.permissionSnapshot().expiresAt())
        .isEqualTo(LocalDateTime.of(2026, 5, 1, 22, 0));
      assertThat(session.permissionSnapshot().permissions())
        .contains(new Permission(new BusinessCode("BIZ-100"), new ActionCode("ACT-VIEW")));
    }

    @Test
    @DisplayName("空 JSON 字符串时 PermissionSnapshot 应为 null（PENDING 状态）")
    void shouldReturnNullSnapshotWhenBlankJson() {
      var doObj = buildPendingDO();
      doObj.setSnapshotPermissions("  ");

      var session = converter.toDomain(doObj);

      assertThat(session.permissionSnapshot()).isNull();
    }
  }

  @Nested
  @DisplayName("CredentialOwner 多态序列化往返")
  class CredentialOwnerRoundTripTest {

    @Test
    @DisplayName("UserCredentialOwner 应能完整往返")
    void shouldRoundTripUserCredentialOwner() {
      var original = authorizedSession(userOwner());

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.credentialOwner()).isInstanceOf(UserCredentialOwner.class);
      assertThat(((UserCredentialOwner) roundTripped.credentialOwner()).userNo())
        .isEqualTo(((UserCredentialOwner) original.credentialOwner()).userNo());
    }

    @Test
    @DisplayName("CustomerCredentialOwner 应能完整往返")
    void shouldRoundTripCustomerCredentialOwner() {
      var original = authorizedSession(customerOwner());

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.credentialOwner()).isInstanceOf(CustomerCredentialOwner.class);
      assertThat(((CustomerCredentialOwner) roundTripped.credentialOwner()).customerNo())
        .isEqualTo(((CustomerCredentialOwner) original.credentialOwner()).customerNo());
    }

    @Test
    @DisplayName("PlanCredentialOwner 应能完整往返")
    void shouldRoundTripPlanCredentialOwner() {
      var original = authorizedSession(planOwner());

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.credentialOwner()).isInstanceOf(PlanCredentialOwner.class);
      assertThat(((PlanCredentialOwner) roundTripped.credentialOwner()).planNo())
        .isEqualTo(((PlanCredentialOwner) original.credentialOwner()).planNo());
    }
  }

  @Nested
  @DisplayName("往返一致性: toDomain(toDO(session))")
  class RoundTripTest {

    @Test
    @DisplayName("AUTHORIZED 会话应能完整往返")
    void shouldRoundTripAuthorizedSession() {
      var original = authorizedSession(userOwner());

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.id().value()).isEqualTo("sec-001");
      assertThat(roundTripped.tellerAccountId().value()).isEqualTo("teller-001");
      assertThat(roundTripped.approverAccountId().value()).isEqualTo("approver-001");
      assertThat(roundTripped.approverMobile().value()).isEqualTo("+8613800138000");
      assertThat(roundTripped.planId().value()).isEqualTo("PLAN-001");
      assertThat(roundTripped.status()).isEqualTo(SecondaryAuthStatus.AUTHORIZED);
      assertThat(roundTripped.initiatedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 0));
      assertThat(roundTripped.pendingExpiresAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 5));
      assertThat(roundTripped.authorizedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 10, 3));
      assertThat(roundTripped.expiresAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 22, 0));
      assertThat(roundTripped.createdBy().value()).isEqualTo("creator-1");
      assertThat(roundTripped.updatedBy().value()).isEqualTo("updater-1");
      assertThat(roundTripped.version().value()).isEqualTo(3L);
    }

    @Test
    @DisplayName("PENDING 会话应能完整往返（含 VerificationCode）")
    void shouldRoundTripPendingSession() {
      var original = pendingSession(userOwner());

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.id().value()).isEqualTo("sec-002");
      assertThat(roundTripped.status()).isEqualTo(SecondaryAuthStatus.PENDING);
      assertThat(roundTripped.verificationCode()).isNotNull();
      assertThat(roundTripped.verificationCode().hashedCode()).isEqualTo("hashed-code-001");
      assertThat(roundTripped.verificationCode().remainingAttempts()).isEqualTo(3);
      assertThat(roundTripped.effectiveIdentity()).isNull();
      assertThat(roundTripped.permissionSnapshot()).isNull();
      assertThat(roundTripped.authorizedAt()).isNull();
    }

    @Test
    @DisplayName("EffectiveIdentity 应能双向往返")
    void shouldRoundTripEffectiveIdentity() {
      var original = authorizedSession(userOwner());

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
    @DisplayName("PermissionSnapshot 应能双向往返")
    void shouldRoundTripPermissionSnapshot() {
      var original = authorizedSession(userOwner());

      var roundTripped = converter.toDomain(converter.toDO(original));

      assertThat(roundTripped.permissionSnapshot()).isNotNull();
      assertThat(roundTripped.permissionSnapshot().frozenAt())
        .isEqualTo(original.permissionSnapshot().frozenAt());
      assertThat(roundTripped.permissionSnapshot().expiresAt())
        .isEqualTo(original.permissionSnapshot().expiresAt());
      assertThat(roundTripped.permissionSnapshot().permissions())
        .isEqualTo(original.permissionSnapshot().permissions());
    }
  }

  /**
   * 构造 AUTHORIZED 状态的 DO 样本（含 effectiveIdentity 和 permissionSnapshot JSON）.
   */
  private SecondaryAuthSessionDO buildAuthorizedDO() {
    var doObj = baseDO();
    doObj.setApproverAccountId("approver-100");
    doObj.setCredentialOwnerType("USER");
    doObj.setCredentialOwnerId("user-100");
    doObj.setVerificationCodeHash(null);
    doObj.setEffectiveIdentityId("approver-100");
    doObj.setEffectiveIdentityActing("teller-100");
    doObj.setEffectiveViaSecondary(true);
    doObj.setSnapshotPermissions(
      "[{\"businessCode\":{\"value\":\"BIZ-100\"},\"actionCode\":{\"value\":\"ACT-VIEW\"}}]");
    doObj.setSnapshotFrozenAt(LocalDateTime.of(2026, 5, 1, 10, 0));
    doObj.setSnapshotExpiresAt(LocalDateTime.of(2026, 5, 1, 22, 0));
    doObj.setStatus("AUTHORIZED");
    doObj.setAuthorizedAt(LocalDateTime.of(2026, 5, 1, 10, 3));
    return doObj;
  }

  /**
   * 构造 PENDING 状态的 DO 样本（含 verificationCode，无 effectiveIdentity 和 permissionSnapshot）.
   */
  private SecondaryAuthSessionDO buildPendingDO() {
    var doObj = baseDO();
    doObj.setId("sec-200");
    doObj.setApproverAccountId(null);
    doObj.setCredentialOwnerType("USER");
    doObj.setCredentialOwnerId("user-200");
    doObj.setVerificationCodeHash("hashed-100");
    doObj.setVerificationSentAt(LocalDateTime.of(2026, 6, 1, 10, 0));
    doObj.setVerificationExpiresAt(LocalDateTime.of(2026, 6, 1, 10, 5));
    doObj.setVerificationRemaining(2);
    doObj.setEffectiveIdentityId(null);
    doObj.setSnapshotPermissions(null);
    doObj.setStatus("PENDING");
    doObj.setAuthorizedAt(null);
    return doObj;
  }

  private SecondaryAuthSessionDO baseDO() {
    var doObj = new SecondaryAuthSessionDO();
    doObj.setId("sec-100");
    doObj.setTellerAccountId("teller-100");
    doObj.setApproverMobile("+8613800138002");
    doObj.setPlanId("PLAN-100");
    doObj.setCreatedBy("creator-100");
    doObj.setUpdatedBy("updater-100");
    doObj.setCreateTime(LocalDateTime.of(2026, 5, 1, 10, 0));
    doObj.setUpdateTime(LocalDateTime.of(2026, 5, 2, 10, 0));
    doObj.setVersion(7);
    doObj.setInitiatedAt(LocalDateTime.of(2026, 5, 1, 10, 0));
    doObj.setPendingExpiresAt(LocalDateTime.of(2026, 5, 1, 10, 5));
    doObj.setExpiresAt(LocalDateTime.of(2026, 5, 1, 22, 0));
    doObj.setRevokeReason(null);
    return doObj;
  }
}
