package com.example.iam.infrastructure.converter;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.infrastructure.entity.CredentialDO;
import com.example.iam.types.CredentialId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CredentialConverter} 单元测试。
 *
 * <p>覆盖 Credential 与 CredentialDO 双向映射、auxData JSON 序列化、
 * 枚举/ID 类型转换、null 输入处理。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("CredentialConverter 转换器测试")
class CredentialConverterTest {

    private final CredentialConverter converter = Mappers.getMapper(CredentialConverter.class);

    private static final Long CREDENTIAL_ID_VALUE = 7001L;
    private static final String OWNER_TYPE = "INTERNET_USER";
    private static final Long OWNER_ID = 5001L;
    private static final CredentialType CREDENTIAL_TYPE = CredentialType.PASSWORD;
    private static final String SECRET_HASH = "$2a$10$abcdefg";
    private static final String SALT = "s4lt";
    private static final CredentialStatus STATUS = CredentialStatus.ACTIVE;
    private static final LocalDateTime EXPIRE_TIME = LocalDateTime.of(2027, 1, 1, 0, 0, 0);
    private static final String OPERATOR = "U-ADMIN";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 1, 10, 0, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 7, 2, 11, 30, 0);
    private static final long VERSION_VALUE = 2L;

    @Nested
    @DisplayName("toDO: Credential -> CredentialDO")
    class ToDOTest {

        @Test
        @DisplayName("完整字段映射:auxData 序列化为 JSON")
        void shouldMapAllFieldsToDO() {
            Map<String, String> auxData = new LinkedHashMap<>();
            auxData.put("counter", "42");
            auxData.put("deviceId", "UKEY-001");
            Credential credential = buildCredential(auxData);

            CredentialDO credentialDO = converter.toDO(credential);

            assertThat(credentialDO).isNotNull();
            assertThat(credentialDO.getId()).isEqualTo(CREDENTIAL_ID_VALUE);
            assertThat(credentialDO.getOwnerType()).isEqualTo(OWNER_TYPE);
            assertThat(credentialDO.getOwnerId()).isEqualTo(OWNER_ID);
            assertThat(credentialDO.getCredentialType()).isEqualTo(CREDENTIAL_TYPE.name());
            assertThat(credentialDO.getSecretHash()).isEqualTo(SECRET_HASH);
            assertThat(credentialDO.getSalt()).isEqualTo(SALT);
            assertThat(credentialDO.getAuxData()).contains("counter").contains("42");
            assertThat(credentialDO.getStatus()).isEqualTo(STATUS.name());
            assertThat(credentialDO.getExpireTime()).isEqualTo(EXPIRE_TIME);
            assertThat(credentialDO.getCreatedBy()).isEqualTo(OPERATOR);
            assertThat(credentialDO.getUpdatedBy()).isEqualTo(OPERATOR);
            assertThat(credentialDO.getCreateTime()).isEqualTo(CREATED_AT);
            assertThat(credentialDO.getUpdateTime()).isEqualTo(UPDATED_AT);
            assertThat(credentialDO.getVersion()).isEqualTo((int) VERSION_VALUE);
            assertThat(credentialDO.getDeleted()).isFalse();
        }

        @Test
        @DisplayName("auxData 为空 Map 时 DO 字段为 null")
        void shouldMapEmptyAuxDataToNull() {
            Credential credential = buildCredential(new LinkedHashMap<>());

            CredentialDO credentialDO = converter.toDO(credential);

            assertThat(credentialDO.getAuxData()).isNull();
        }

        @Test
        @DisplayName("expireTime 为 null(永久凭据)时正确映射")
        void shouldMapNullExpireTime() {
            Credential credential = Credential.reconstitute(
                    CredentialId.of(CREDENTIAL_ID_VALUE), OWNER_TYPE, OWNER_ID,
                    CREDENTIAL_TYPE, SECRET_HASH, SALT, new LinkedHashMap<>(),
                    STATUS, null,
                    UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                    CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));

            CredentialDO credentialDO = converter.toDO(credential);

            assertThat(credentialDO.getExpireTime()).isNull();
        }

        @Test
        @DisplayName("REVOKED 状态正确映射为字符串")
        void shouldMapRevokedStatus() {
            Credential credential = Credential.reconstitute(
                    CredentialId.of(CREDENTIAL_ID_VALUE), OWNER_TYPE, OWNER_ID,
                    CREDENTIAL_TYPE, SECRET_HASH, SALT, new LinkedHashMap<>(),
                    CredentialStatus.REVOKED, EXPIRE_TIME,
                    UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                    CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));

            CredentialDO credentialDO = converter.toDO(credential);

            assertThat(credentialDO.getStatus()).isEqualTo("REVOKED");
        }
    }

    @Nested
    @DisplayName("toDomain: CredentialDO -> Credential")
    class ToDomainTest {

        @Test
        @DisplayName("完整字段映射:auxData 反序列化为 Map")
        void shouldMapAllFieldsToDomain() {
            CredentialDO credentialDO = buildCredentialDO("{\"counter\":\"42\",\"deviceId\":\"UKEY-001\"}");

            Credential credential = converter.toDomain(credentialDO);

            assertThat(credential).isNotNull();
            assertThat(credential.id().value()).isEqualTo(CREDENTIAL_ID_VALUE);
            assertThat(credential.ownerType()).isEqualTo(OWNER_TYPE);
            assertThat(credential.ownerId()).isEqualTo(OWNER_ID);
            assertThat(credential.credentialType()).isEqualTo(CREDENTIAL_TYPE);
            assertThat(credential.secretHash()).isEqualTo(SECRET_HASH);
            assertThat(credential.salt()).isEqualTo(SALT);
            assertThat(credential.auxData())
                    .containsEntry("counter", "42")
                    .containsEntry("deviceId", "UKEY-001");
            assertThat(credential.status()).isEqualTo(STATUS);
            assertThat(credential.expireTime()).isEqualTo(EXPIRE_TIME);
            assertThat(credential.createdBy().value()).isEqualTo(OPERATOR);
            assertThat(credential.updatedBy().value()).isEqualTo(OPERATOR);
            assertThat(credential.createdAt()).isEqualTo(CREATED_AT);
            assertThat(credential.updatedAt()).isEqualTo(UPDATED_AT);
            assertThat(credential.version().value()).isEqualTo(VERSION_VALUE);
        }

        @Test
        @DisplayName("DO 为 null 时返回 null")
        void shouldReturnNullWhenDOIsNull() {
            assertThat(converter.toDomain(null)).isNull();
        }

        @Test
        @DisplayName("auxData 为 null 时反序列化为空 Map")
        void shouldDeserializeNullAuxData() {
            CredentialDO credentialDO = buildCredentialDO(null);

            Credential credential = converter.toDomain(credentialDO);

            assertThat(credential.auxData()).isEmpty();
        }

        @Test
        @DisplayName("auxData 为空字符串时反序列化为空 Map")
        void shouldDeserializeBlankAuxData() {
            CredentialDO credentialDO = buildCredentialDO("  ");

            Credential credential = converter.toDomain(credentialDO);

            assertThat(credential.auxData()).isEmpty();
        }
    }

    @Nested
    @DisplayName("JSON 辅助方法")
    class JsonHelperTest {

        @Test
        @DisplayName("mapToJson: null 返回 null")
        void shouldReturnNullForNullMap() {
            assertThat(converter.mapToJson(null)).isNull();
        }

        @Test
        @DisplayName("mapToJson: 空 Map 返回 null")
        void shouldReturnNullForEmptyMap() {
            assertThat(converter.mapToJson(new LinkedHashMap<>())).isNull();
        }

        @Test
        @DisplayName("jsonToMap: null 返回空 Map")
        void shouldReturnEmptyMapForNullJson() {
            assertThat(converter.jsonToMap(null)).isEmpty();
        }

        @Test
        @DisplayName("双向转换:Map -> JSON -> Map 保持一致")
        void shouldRoundTripMapAndJson() {
            Map<String, String> original = new LinkedHashMap<>();
            original.put("k1", "v1");
            original.put("k2", "v2");

            String json = converter.mapToJson(original);
            Map<String, String> rebuilt = converter.jsonToMap(json);

            assertThat(rebuilt).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("枚举与 ID 类型转换")
    class TypeConversionTest {

        @Test
        @DisplayName("toCredentialType: null 返回 null")
        void shouldReturnNullCredentialTypeForNullString() {
            assertThat(converter.toCredentialType(null)).isNull();
        }

        @Test
        @DisplayName("toCredentialStatus: null 返回 null")
        void shouldReturnNullCredentialStatusForNullString() {
            assertThat(converter.toCredentialStatus(null)).isNull();
        }

        @Test
        @DisplayName("toVersion: null Integer 返回 null")
        void shouldReturnNullVersionForNullInteger() {
            assertThat(converter.toVersion(null)).isNull();
        }
    }

    @Nested
    @DisplayName("双向转换一致性")
    class RoundTripTest {

        @Test
        @DisplayName("toDomain(toDO(credential)) 关键字段一致")
        void shouldPreserveKeyFieldsThroughRoundTrip() {
            Map<String, String> auxData = new LinkedHashMap<>();
            auxData.put("counter", "42");
            Credential original = buildCredential(auxData);

            CredentialDO intermediateDO = converter.toDO(original);
            Credential rebuilt = converter.toDomain(intermediateDO);

            assertThat(rebuilt.id()).isEqualTo(original.id());
            assertThat(rebuilt.ownerType()).isEqualTo(original.ownerType());
            assertThat(rebuilt.ownerId()).isEqualTo(original.ownerId());
            assertThat(rebuilt.credentialType()).isEqualTo(original.credentialType());
            assertThat(rebuilt.secretHash()).isEqualTo(original.secretHash());
            assertThat(rebuilt.salt()).isEqualTo(original.salt());
            assertThat(rebuilt.auxData()).isEqualTo(original.auxData());
            assertThat(rebuilt.status()).isEqualTo(original.status());
            assertThat(rebuilt.expireTime()).isEqualTo(original.expireTime());
            assertThat(rebuilt.createdBy()).isEqualTo(original.createdBy());
            assertThat(rebuilt.updatedBy()).isEqualTo(original.updatedBy());
            assertThat(rebuilt.createdAt()).isEqualTo(original.createdAt());
            assertThat(rebuilt.updatedAt()).isEqualTo(original.updatedAt());
            assertThat(rebuilt.version()).isEqualTo(original.version());
        }
    }

    private Credential buildCredential(Map<String, String> auxData) {
        return Credential.reconstitute(
                CredentialId.of(CREDENTIAL_ID_VALUE), OWNER_TYPE, OWNER_ID,
                CREDENTIAL_TYPE, SECRET_HASH, SALT, auxData,
                STATUS, EXPIRE_TIME,
                UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));
    }

    private CredentialDO buildCredentialDO(String auxDataJson) {
        CredentialDO credentialDO = new CredentialDO();
        credentialDO.setId(CREDENTIAL_ID_VALUE);
        credentialDO.setOwnerType(OWNER_TYPE);
        credentialDO.setOwnerId(OWNER_ID);
        credentialDO.setCredentialType(CREDENTIAL_TYPE.name());
        credentialDO.setSecretHash(SECRET_HASH);
        credentialDO.setSalt(SALT);
        credentialDO.setAuxData(auxDataJson);
        credentialDO.setStatus(STATUS.name());
        credentialDO.setExpireTime(EXPIRE_TIME);
        credentialDO.setCreatedBy(OPERATOR);
        credentialDO.setUpdatedBy(OPERATOR);
        credentialDO.setCreateTime(CREATED_AT);
        credentialDO.setUpdateTime(UPDATED_AT);
        credentialDO.setVersion((int) VERSION_VALUE);
        credentialDO.setDeleted(false);
        return credentialDO;
    }
}
