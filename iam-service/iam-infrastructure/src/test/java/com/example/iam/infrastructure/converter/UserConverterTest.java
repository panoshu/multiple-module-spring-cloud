package com.example.iam.infrastructure.converter;

import com.example.iam.domain.authentication.aggregate.entity.UserProfile;
import com.example.iam.domain.authentication.aggregate.root.User;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.infrastructure.entity.UserDO;
import com.example.iam.infrastructure.entity.UserProfileDO;
import com.example.iam.types.UserId;
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
 * {@link UserConverter} 单元测试。
 *
 * <p>覆盖 User/UserProfile 与 UserDO/UserProfileDO 之间的双向映射、
 * extraAttributes JSON 序列化、null 输入处理、枚举与 ID 类型转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("UserConverter 转换器测试")
class UserConverterTest {

    private final UserConverter converter = Mappers.getMapper(UserConverter.class);

    private static final Long USER_ID_VALUE = 5001L;
    private static final ChannelType CHANNEL_TYPE = ChannelType.INTERNET;
    private static final String LOGIN_NAME = "alice";
    private static final String DISPLAY_NAME = "爱丽丝";
    private static final UserStatus STATUS = UserStatus.ACTIVE;
    private static final LocalDateTime LAST_LOGIN_TIME = LocalDateTime.of(2026, 7, 2, 9, 30, 0);
    private static final String LAST_LOGIN_IP = "10.0.0.1";
    private static final String OPERATOR = "U-ADMIN";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 1, 10, 0, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 7, 2, 11, 30, 0);
    private static final long VERSION_VALUE = 5L;

    // Profile 相关
    private static final String EMAIL = "alice@example.com";
    private static final String PHONE = "13800000001";
    private static final String ORGANIZATION = "技术部";
    private static final String POSITION = "工程师";
    private static final String EMPLOYEE_NO = "EMP-001";

    @Nested
    @DisplayName("toDO: User -> UserDO")
    class ToDOTest {

        @Test
        @DisplayName("完整字段映射:无档案时 profile 相关字段为 null")
        void shouldMapAllFieldsWithoutProfile() {
            User user = buildUserWithoutProfile();

            UserDO userDO = converter.toDO(user);

            assertThat(userDO).isNotNull();
            assertThat(userDO.getId()).isEqualTo(USER_ID_VALUE);
            assertThat(userDO.getChannelType()).isEqualTo(CHANNEL_TYPE.name());
            assertThat(userDO.getLoginName()).isEqualTo(LOGIN_NAME);
            assertThat(userDO.getDisplayName()).isEqualTo(DISPLAY_NAME);
            assertThat(userDO.getStatus()).isEqualTo(STATUS.name());
            assertThat(userDO.getLastLoginTime()).isEqualTo(LAST_LOGIN_TIME);
            assertThat(userDO.getLastLoginIp()).isEqualTo(LAST_LOGIN_IP);
            assertThat(userDO.getCreatedBy()).isEqualTo(OPERATOR);
            assertThat(userDO.getUpdatedBy()).isEqualTo(OPERATOR);
            assertThat(userDO.getCreateTime()).isEqualTo(CREATED_AT);
            assertThat(userDO.getUpdateTime()).isEqualTo(UPDATED_AT);
            assertThat(userDO.getVersion()).isEqualTo((int) VERSION_VALUE);
            assertThat(userDO.getDeleted()).isFalse();
        }

        @Test
        @DisplayName("LOCKED 状态正确映射为字符串")
        void shouldMapLockedStatus() {
            User user = User.reconstitute(
                    UserId.of(USER_ID_VALUE), CHANNEL_TYPE, LOGIN_NAME, DISPLAY_NAME,
                    UserStatus.LOCKED, LAST_LOGIN_TIME, LAST_LOGIN_IP, null,
                    UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                    CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));

            UserDO userDO = converter.toDO(user);

            assertThat(userDO.getStatus()).isEqualTo("LOCKED");
        }
    }

    @Nested
    @DisplayName("toProfileDO: UserProfile -> UserProfileDO")
    class ToProfileDOTest {

        @Test
        @DisplayName("完整字段映射:extraAttributes 序列化为 JSON")
        void shouldMapProfileWithExtraAttributes() {
            Map<String, String> extras = new LinkedHashMap<>();
            extras.put("clearance", "L3");
            extras.put("branch", "BJ-001");
            UserProfile profile = buildProfile(extras);

            UserProfileDO profileDO = converter.toProfileDO(profile);

            assertThat(profileDO).isNotNull();
            assertThat(profileDO.getUserId()).isEqualTo(USER_ID_VALUE);
            assertThat(profileDO.getChannelType()).isEqualTo(CHANNEL_TYPE.name());
            assertThat(profileDO.getEmail()).isEqualTo(EMAIL);
            assertThat(profileDO.getPhone()).isEqualTo(PHONE);
            assertThat(profileDO.getOrganization()).isEqualTo(ORGANIZATION);
            assertThat(profileDO.getPosition()).isEqualTo(POSITION);
            assertThat(profileDO.getEmployeeNo()).isEqualTo(EMPLOYEE_NO);
            assertThat(profileDO.getExtraAttributes()).contains("clearance").contains("L3");
            assertThat(profileDO.getCreatedBy()).isEqualTo(OPERATOR);
            assertThat(profileDO.getUpdatedBy()).isEqualTo(OPERATOR);
            assertThat(profileDO.getCreateTime()).isEqualTo(CREATED_AT);
            assertThat(profileDO.getUpdateTime()).isEqualTo(UPDATED_AT);
            assertThat(profileDO.getVersion()).isEqualTo((int) VERSION_VALUE);
            assertThat(profileDO.getDeleted()).isFalse();
        }

        @Test
        @DisplayName("extraAttributes 为空 Map 时 DO 字段为 null")
        void shouldMapEmptyExtraAttributesToNull() {
            UserProfile profile = buildProfile(new LinkedHashMap<>());

            UserProfileDO profileDO = converter.toProfileDO(profile);

            assertThat(profileDO.getExtraAttributes()).isNull();
        }
    }

    @Nested
    @DisplayName("toDomain: (UserDO, UserProfileDO) -> User")
    class ToDomainTest {

        @Test
        @DisplayName("完整字段映射:含档案")
        void shouldMapToDomainWithProfile() {
            UserDO userDO = buildUserDO();
            UserProfileDO profileDO = buildProfileDO("{\"clearance\":\"L3\"}");

            User user = converter.toDomain(userDO, profileDO);

            assertThat(user).isNotNull();
            assertThat(user.id().value()).isEqualTo(USER_ID_VALUE);
            assertThat(user.channelType()).isEqualTo(CHANNEL_TYPE);
            assertThat(user.loginName()).isEqualTo(LOGIN_NAME);
            assertThat(user.displayName()).isEqualTo(DISPLAY_NAME);
            assertThat(user.status()).isEqualTo(STATUS);
            assertThat(user.lastLoginTime()).isEqualTo(LAST_LOGIN_TIME);
            assertThat(user.lastLoginIp()).isEqualTo(LAST_LOGIN_IP);
            assertThat(user.profile()).isNotNull();
            assertThat(user.profile().email()).isEqualTo(EMAIL);
            assertThat(user.profile().extraAttributes()).containsEntry("clearance", "L3");
            assertThat(user.createdBy().value()).isEqualTo(OPERATOR);
            assertThat(user.updatedBy().value()).isEqualTo(OPERATOR);
            assertThat(user.createdAt()).isEqualTo(CREATED_AT);
            assertThat(user.updatedAt()).isEqualTo(UPDATED_AT);
            assertThat(user.version().value()).isEqualTo(VERSION_VALUE);
        }

        @Test
        @DisplayName("profileDO 为 null 时重建 User 档案为 null")
        void shouldMapToDomainWithNullProfile() {
            UserDO userDO = buildUserDO();

            User user = converter.toDomain(userDO, null);

            assertThat(user).isNotNull();
            assertThat(user.profile()).isNull();
        }

        @Test
        @DisplayName("userDO 为 null 时返回 null")
        void shouldReturnNullWhenUserDOIsNull() {
            assertThat(converter.toDomain(null, buildProfileDO("{}"))).isNull();
        }

        @Test
        @DisplayName("extraAttributes 为空字符串时反序列化为空 Map")
        void shouldDeserializeBlankExtraAttributes() {
            UserProfileDO profileDO = buildProfileDO("");

            UserProfile profile = converter.toProfileDomain(profileDO);

            assertThat(profile).isNotNull();
            assertThat(profile.extraAttributes()).isEmpty();
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
        @DisplayName("jsonToMap: 空白字符串返回空 Map")
        void shouldReturnEmptyMapForBlankJson() {
            assertThat(converter.jsonToMap("   ")).isEmpty();
        }

        @Test
        @DisplayName("双向转换:Map -> JSON -> Map 保持一致")
        void shouldRoundTripMapAndJson() {
            Map<String, String> original = new LinkedHashMap<>();
            original.put("key1", "value1");
            original.put("key2", "value2");

            String json = converter.mapToJson(original);
            Map<String, String> rebuilt = converter.jsonToMap(json);

            assertThat(rebuilt).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("枚举与 ID 类型转换")
    class TypeConversionTest {

        @Test
        @DisplayName("toChannelType: null 字符串返回 null")
        void shouldReturnNullChannelTypeForNullString() {
            assertThat(converter.toChannelType(null)).isNull();
        }

        @Test
        @DisplayName("toUserStatus: null 字符串返回 null")
        void shouldReturnNullUserStatusForNullString() {
            assertThat(converter.toUserStatus(null)).isNull();
        }

        @Test
        @DisplayName("toUserNo: null 字符串返回 null")
        void shouldReturnNullUserNoForNullString() {
            assertThat(converter.toUserNo(null)).isNull();
        }

        @Test
        @DisplayName("toVersion: null Integer 返回 null")
        void shouldReturnNullVersionForNullInteger() {
            assertThat(converter.toVersion(null)).isNull();
        }

        @Test
        @DisplayName("toVersion: Integer 正确转换为 Version")
        void shouldConvertIntegerToVersion() {
            assertThat(converter.toVersion(7).value()).isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("双向转换一致性")
    class RoundTripTest {

        @Test
        @DisplayName("toDomain(toDO(user), toProfileDO(profile)) 关键字段一致")
        void shouldPreserveKeyFieldsThroughRoundTrip() {
            Map<String, String> extras = new LinkedHashMap<>();
            extras.put("clearance", "L3");
            User original = buildUserWithProfile(extras);

            UserDO userDO = converter.toDO(original);
            UserProfileDO profileDO = converter.toProfileDO(original.profile());
            User rebuilt = converter.toDomain(userDO, profileDO);

            assertThat(rebuilt.id()).isEqualTo(original.id());
            assertThat(rebuilt.channelType()).isEqualTo(original.channelType());
            assertThat(rebuilt.loginName()).isEqualTo(original.loginName());
            assertThat(rebuilt.displayName()).isEqualTo(original.displayName());
            assertThat(rebuilt.status()).isEqualTo(original.status());
            assertThat(rebuilt.lastLoginTime()).isEqualTo(original.lastLoginTime());
            assertThat(rebuilt.lastLoginIp()).isEqualTo(original.lastLoginIp());
            assertThat(rebuilt.profile().email()).isEqualTo(original.profile().email());
            assertThat(rebuilt.profile().phone()).isEqualTo(original.profile().phone());
            assertThat(rebuilt.profile().extraAttributes()).isEqualTo(original.profile().extraAttributes());
            assertThat(rebuilt.createdBy()).isEqualTo(original.createdBy());
            assertThat(rebuilt.updatedBy()).isEqualTo(original.updatedBy());
            assertThat(rebuilt.createdAt()).isEqualTo(original.createdAt());
            assertThat(rebuilt.updatedAt()).isEqualTo(original.updatedAt());
            assertThat(rebuilt.version()).isEqualTo(original.version());
        }
    }

    private User buildUserWithoutProfile() {
        return User.reconstitute(
                UserId.of(USER_ID_VALUE), CHANNEL_TYPE, LOGIN_NAME, DISPLAY_NAME,
                STATUS, LAST_LOGIN_TIME, LAST_LOGIN_IP, null,
                UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));
    }

    private User buildUserWithProfile(Map<String, String> extras) {
        UserProfile profile = buildProfile(extras);
        return User.reconstitute(
                UserId.of(USER_ID_VALUE), CHANNEL_TYPE, LOGIN_NAME, DISPLAY_NAME,
                STATUS, LAST_LOGIN_TIME, LAST_LOGIN_IP, profile,
                UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));
    }

    private UserProfile buildProfile(Map<String, String> extras) {
        return UserProfile.reconstitute(
                UserId.of(USER_ID_VALUE), CHANNEL_TYPE,
                EMAIL, PHONE, ORGANIZATION, POSITION,
                null, EMPLOYEE_NO, extras,
                UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));
    }

    private UserDO buildUserDO() {
        UserDO userDO = new UserDO();
        userDO.setId(USER_ID_VALUE);
        userDO.setChannelType(CHANNEL_TYPE.name());
        userDO.setLoginName(LOGIN_NAME);
        userDO.setDisplayName(DISPLAY_NAME);
        userDO.setStatus(STATUS.name());
        userDO.setLastLoginTime(LAST_LOGIN_TIME);
        userDO.setLastLoginIp(LAST_LOGIN_IP);
        userDO.setCreatedBy(OPERATOR);
        userDO.setUpdatedBy(OPERATOR);
        userDO.setCreateTime(CREATED_AT);
        userDO.setUpdateTime(UPDATED_AT);
        userDO.setVersion((int) VERSION_VALUE);
        userDO.setDeleted(false);
        return userDO;
    }

    private UserProfileDO buildProfileDO(String extraAttributesJson) {
        UserProfileDO profileDO = new UserProfileDO();
        profileDO.setUserId(USER_ID_VALUE);
        profileDO.setChannelType(CHANNEL_TYPE.name());
        profileDO.setEmail(EMAIL);
        profileDO.setPhone(PHONE);
        profileDO.setOrganization(ORGANIZATION);
        profileDO.setPosition(POSITION);
        profileDO.setEmployeeNo(EMPLOYEE_NO);
        profileDO.setExtraAttributes(extraAttributesJson);
        profileDO.setCreatedBy(OPERATOR);
        profileDO.setUpdatedBy(OPERATOR);
        profileDO.setCreateTime(CREATED_AT);
        profileDO.setUpdateTime(UPDATED_AT);
        profileDO.setVersion((int) VERSION_VALUE);
        profileDO.setDeleted(false);
        return profileDO;
    }
}
