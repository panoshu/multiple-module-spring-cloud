package com.example.iam.infrastructure.converter;

import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.infrastructure.entity.SecondaryAuthSessionDO;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SecondaryAuthSessionConverter} 单元测试。
 *
 * <p>覆盖 SecondaryAuthSession 与 SecondaryAuthSessionDO 双向映射、
 * permissionSnapshot JSON 序列化、null 输入处理。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("SecondaryAuthSessionConverter 转换器测试")
class SecondaryAuthSessionConverterTest {

    private final SecondaryAuthSessionConverter converter =
            Mappers.getMapper(SecondaryAuthSessionConverter.class);

    private static final Long SESSION_ID_VALUE = 3001L;
    private static final Long TELLER_ID = 5001L;
    private static final Long APPROVER_ID = 5002L;
    private static final String CUSTOMER_NO = "C-001";
    private static final String PLAN_ID = "P-001";
    private static final SecondaryAuthStatus STATUS = SecondaryAuthStatus.AUTHORIZED;
    private static final LocalDateTime INITIATED_AT = LocalDateTime.of(2026, 7, 2, 9, 0, 0);
    private static final LocalDateTime AUTHORIZED_AT = LocalDateTime.of(2026, 7, 2, 9, 5, 0);
    private static final LocalDateTime EXPIRE_AT = LocalDateTime.of(2026, 7, 2, 17, 0, 0);
    private static final String REVOKE_REASON = "操作完成主动撤销";
    private static final String OPERATOR = "U-TELLER";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 2, 9, 0, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 7, 2, 9, 5, 0);
    private static final long VERSION_VALUE = 1L;

    private static final PermissionCode PERM_1 = PermissionCode.of("ANNUITY_ESTABLISH.HANDLE");
    private static final PermissionCode PERM_2 = PermissionCode.of("ANNUITY_CONTRIBUTION.QUERY");

    @Nested
    @DisplayName("toDO: SecondaryAuthSession -> SecondaryAuthSessionDO")
    class ToDOTest {

        @Test
        @DisplayName("完整字段映射:permissionSnapshot 序列化为 JSON 数组")
        void shouldMapAllFieldsToDO() {
            SecondaryAuthSession session = buildSession();

            SecondaryAuthSessionDO sessionDO = converter.toDO(session);

            assertThat(sessionDO).isNotNull();
            assertThat(sessionDO.getId()).isEqualTo(SESSION_ID_VALUE);
            assertThat(sessionDO.getTellerId()).isEqualTo(TELLER_ID);
            assertThat(sessionDO.getApproverId()).isEqualTo(APPROVER_ID);
            assertThat(sessionDO.getCustomerNo()).isEqualTo(CUSTOMER_NO);
            assertThat(sessionDO.getPlanId()).isEqualTo(PLAN_ID);
            assertThat(sessionDO.getPermissionSnapshot())
                    .contains("ANNUITY_ESTABLISH.HANDLE")
                    .contains("ANNUITY_CONTRIBUTION.QUERY");
            assertThat(sessionDO.getStatus()).isEqualTo(STATUS.name());
            assertThat(sessionDO.getInitiatedAt()).isEqualTo(INITIATED_AT);
            assertThat(sessionDO.getAuthorizedAt()).isEqualTo(AUTHORIZED_AT);
            assertThat(sessionDO.getExpireAt()).isEqualTo(EXPIRE_AT);
            assertThat(sessionDO.getRevokeReason()).isEqualTo(REVOKE_REASON);
            assertThat(sessionDO.getCreatedBy()).isEqualTo(OPERATOR);
            assertThat(sessionDO.getUpdatedBy()).isEqualTo(OPERATOR);
            assertThat(sessionDO.getCreateTime()).isEqualTo(CREATED_AT);
            assertThat(sessionDO.getUpdateTime()).isEqualTo(UPDATED_AT);
            assertThat(sessionDO.getVersion()).isEqualTo((int) VERSION_VALUE);
            assertThat(sessionDO.getDeleted()).isFalse();
        }

        @Test
        @DisplayName("permissionSnapshot 为 null 时 DO 字段为 null")
        void shouldMapNullSnapshotToNull() {
            SecondaryAuthSession session = SecondaryAuthSession.reconstitute(
                    SecondaryAuthSessionId.of(SESSION_ID_VALUE),
                    TELLER_ID, APPROVER_ID, CUSTOMER_NO, PLAN_ID,
                    null, SecondaryAuthStatus.PENDING,
                    INITIATED_AT, null, null, null,
                    UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                    CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));

            SecondaryAuthSessionDO sessionDO = converter.toDO(session);

            assertThat(sessionDO.getPermissionSnapshot()).isNull();
        }

        @Test
        @DisplayName("PENDING 状态正确映射,authorizedAt/expireAt 为 null")
        void shouldMapPendingSession() {
            SecondaryAuthSession session = SecondaryAuthSession.reconstitute(
                    SecondaryAuthSessionId.of(SESSION_ID_VALUE),
                    TELLER_ID, APPROVER_ID, CUSTOMER_NO, PLAN_ID,
                    null, SecondaryAuthStatus.PENDING,
                    INITIATED_AT, null, null, null,
                    UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                    CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));

            SecondaryAuthSessionDO sessionDO = converter.toDO(session);

            assertThat(sessionDO.getStatus()).isEqualTo("PENDING");
            assertThat(sessionDO.getAuthorizedAt()).isNull();
            assertThat(sessionDO.getExpireAt()).isNull();
        }
    }

    @Nested
    @DisplayName("toDomain: SecondaryAuthSessionDO -> SecondaryAuthSession")
    class ToDomainTest {

        @Test
        @DisplayName("完整字段映射:permissionSnapshot 反序列化为 Set<PermissionCode>")
        void shouldMapAllFieldsToDomain() {
            SecondaryAuthSessionDO sessionDO = buildSessionDO(
                    "[\"ANNUITY_ESTABLISH.HANDLE\",\"ANNUITY_CONTRIBUTION.QUERY\"]");

            SecondaryAuthSession session = converter.toDomain(sessionDO);

            assertThat(session).isNotNull();
            assertThat(session.id().value()).isEqualTo(SESSION_ID_VALUE);
            assertThat(session.tellerId()).isEqualTo(TELLER_ID);
            assertThat(session.approverId()).isEqualTo(APPROVER_ID);
            assertThat(session.customerNo()).isEqualTo(CUSTOMER_NO);
            assertThat(session.planId()).isEqualTo(PLAN_ID);
            assertThat(session.permissionSnapshot())
                    .containsExactlyInAnyOrder(PERM_1, PERM_2);
            assertThat(session.status()).isEqualTo(STATUS);
            assertThat(session.initiatedAt()).isEqualTo(INITIATED_AT);
            assertThat(session.authorizedAt()).isEqualTo(AUTHORIZED_AT);
            assertThat(session.expireAt()).isEqualTo(EXPIRE_AT);
            assertThat(session.revokeReason()).isEqualTo(REVOKE_REASON);
            assertThat(session.createdBy().value()).isEqualTo(OPERATOR);
            assertThat(session.updatedBy().value()).isEqualTo(OPERATOR);
            assertThat(session.createdAt()).isEqualTo(CREATED_AT);
            assertThat(session.updatedAt()).isEqualTo(UPDATED_AT);
            assertThat(session.version().value()).isEqualTo(VERSION_VALUE);
        }

        @Test
        @DisplayName("DO 为 null 时返回 null")
        void shouldReturnNullWhenDOIsNull() {
            assertThat(converter.toDomain(null)).isNull();
        }

        @Test
        @DisplayName("permissionSnapshot 为 null 时反序列化为 null")
        void shouldDeserializeNullSnapshot() {
            SecondaryAuthSessionDO sessionDO = buildSessionDO(null);

            SecondaryAuthSession session = converter.toDomain(sessionDO);

            assertThat(session.permissionSnapshot()).isNull();
        }

        @Test
        @DisplayName("permissionSnapshot 为空字符串时反序列化为 null")
        void shouldDeserializeBlankSnapshot() {
            SecondaryAuthSessionDO sessionDO = buildSessionDO("  ");

            SecondaryAuthSession session = converter.toDomain(sessionDO);

            assertThat(session.permissionSnapshot()).isNull();
        }
    }

    @Nested
    @DisplayName("JSON 辅助方法")
    class JsonHelperTest {

        @Test
        @DisplayName("permissionSetToJson: null 返回 null")
        void shouldReturnNullForNullSet() {
            assertThat(converter.permissionSetToJson(null)).isNull();
        }

        @Test
        @DisplayName("permissionSetToJson: 空 Set 返回 null")
        void shouldReturnNullForEmptySet() {
            assertThat(converter.permissionSetToJson(new LinkedHashSet<>())).isNull();
        }

        @Test
        @DisplayName("jsonToPermissionSet: null 返回 null")
        void shouldReturnNullForNullJson() {
            assertThat(converter.jsonToPermissionSet(null)).isNull();
        }

        @Test
        @DisplayName("jsonToPermissionSet: 空白字符串返回 null")
        void shouldReturnNullForBlankJson() {
            assertThat(converter.jsonToPermissionSet("   ")).isNull();
        }

        @Test
        @DisplayName("双向转换:Set -> JSON -> Set 保持一致")
        void shouldRoundTripSetAndJson() {
            Set<PermissionCode> original = new LinkedHashSet<>();
            original.add(PERM_1);
            original.add(PERM_2);

            String json = converter.permissionSetToJson(original);
            Set<PermissionCode> rebuilt = converter.jsonToPermissionSet(json);

            assertThat(rebuilt).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("枚举与 ID 类型转换")
    class TypeConversionTest {

        @Test
        @DisplayName("toSecondaryAuthStatus: null 返回 null")
        void shouldReturnNullStatusForNullString() {
            assertThat(converter.toSecondaryAuthStatus(null)).isNull();
        }

        @Test
        @DisplayName("toVersion: null 返回 null")
        void shouldReturnNullVersionForNullInteger() {
            assertThat(converter.toVersion(null)).isNull();
        }
    }

    @Nested
    @DisplayName("双向转换一致性")
    class RoundTripTest {

        @Test
        @DisplayName("toDomain(toDO(session)) 关键字段一致")
        void shouldPreserveKeyFieldsThroughRoundTrip() {
            SecondaryAuthSession original = buildSession();

            SecondaryAuthSessionDO intermediateDO = converter.toDO(original);
            SecondaryAuthSession rebuilt = converter.toDomain(intermediateDO);

            assertThat(rebuilt.id()).isEqualTo(original.id());
            assertThat(rebuilt.tellerId()).isEqualTo(original.tellerId());
            assertThat(rebuilt.approverId()).isEqualTo(original.approverId());
            assertThat(rebuilt.customerNo()).isEqualTo(original.customerNo());
            assertThat(rebuilt.planId()).isEqualTo(original.planId());
            assertThat(rebuilt.permissionSnapshot()).isEqualTo(original.permissionSnapshot());
            assertThat(rebuilt.status()).isEqualTo(original.status());
            assertThat(rebuilt.initiatedAt()).isEqualTo(original.initiatedAt());
            assertThat(rebuilt.authorizedAt()).isEqualTo(original.authorizedAt());
            assertThat(rebuilt.expireAt()).isEqualTo(original.expireAt());
            assertThat(rebuilt.revokeReason()).isEqualTo(original.revokeReason());
            assertThat(rebuilt.createdBy()).isEqualTo(original.createdBy());
            assertThat(rebuilt.updatedBy()).isEqualTo(original.updatedBy());
            assertThat(rebuilt.version()).isEqualTo(original.version());
        }
    }

    private SecondaryAuthSession buildSession() {
        Set<PermissionCode> snapshot = new LinkedHashSet<>();
        snapshot.add(PERM_1);
        snapshot.add(PERM_2);
        return SecondaryAuthSession.reconstitute(
                SecondaryAuthSessionId.of(SESSION_ID_VALUE),
                TELLER_ID, APPROVER_ID, CUSTOMER_NO, PLAN_ID,
                snapshot, STATUS,
                INITIATED_AT, AUTHORIZED_AT, EXPIRE_AT, REVOKE_REASON,
                UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));
    }

    private SecondaryAuthSessionDO buildSessionDO(String permissionSnapshotJson) {
        SecondaryAuthSessionDO sessionDO = new SecondaryAuthSessionDO();
        sessionDO.setId(SESSION_ID_VALUE);
        sessionDO.setTellerId(TELLER_ID);
        sessionDO.setApproverId(APPROVER_ID);
        sessionDO.setCustomerNo(CUSTOMER_NO);
        sessionDO.setPlanId(PLAN_ID);
        sessionDO.setPermissionSnapshot(permissionSnapshotJson);
        sessionDO.setStatus(STATUS.name());
        sessionDO.setInitiatedAt(INITIATED_AT);
        sessionDO.setAuthorizedAt(AUTHORIZED_AT);
        sessionDO.setExpireAt(EXPIRE_AT);
        sessionDO.setRevokeReason(REVOKE_REASON);
        sessionDO.setCreatedBy(OPERATOR);
        sessionDO.setUpdatedBy(OPERATOR);
        sessionDO.setCreateTime(CREATED_AT);
        sessionDO.setUpdateTime(UPDATED_AT);
        sessionDO.setVersion((int) VERSION_VALUE);
        sessionDO.setDeleted(false);
        return sessionDO;
    }
}
