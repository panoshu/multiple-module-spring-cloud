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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Map;

/**
 * 用户聚合根转换器。
 *
 * <p>负责 {@link User}+{@link UserProfile} 与 {@link UserDO}+{@link UserProfileDO} 之间的转换。
 * 扩展字段 {@code extraAttributes} 以 JSON 字符串持久化,通过 Jackson 进行序列化/反序列化。
 *
 * <p>时间戳映射遵循项目规范:由应用层管理,Converter 直接从领域对象 {@code createdAt()/updatedAt()}
 * 映射到 DO 的 {@code createTime/updateTime},不使用 ORM 自动填充。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper(componentModel = "spring")
public interface UserConverter {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mapping(target = "id", expression = "java(user.id() != null ? user.id().value() : null)")
    @Mapping(target = "channelType", expression = "java(user.channelType() != null ? user.channelType().name() : null)")
    @Mapping(target = "loginName", expression = "java(user.loginName())")
    @Mapping(target = "displayName", expression = "java(user.displayName())")
    @Mapping(target = "status", expression = "java(user.status() != null ? user.status().name() : null)")
    @Mapping(target = "lastLoginTime", expression = "java(user.lastLoginTime())")
    @Mapping(target = "lastLoginIp", expression = "java(user.lastLoginIp())")
    @Mapping(target = "createdBy", expression = "java(user.createdBy() != null ? user.createdBy().value() : null)")
    @Mapping(target = "updatedBy", expression = "java(user.updatedBy() != null ? user.updatedBy().value() : null)")
    @Mapping(target = "createTime", expression = "java(user.createdAt())")
    @Mapping(target = "updateTime", expression = "java(user.updatedAt())")
    @Mapping(target = "version", expression = "java(user.version() != null ? (int) user.version().value() : null)")
    @Mapping(target = "deleted", constant = "false")
    UserDO toDO(User user);

    @Mapping(target = "userId", expression = "java(profile.id() != null ? profile.id().value() : null)")
    @Mapping(target = "channelType", expression = "java(profile.channelType() != null ? profile.channelType().name() : null)")
    @Mapping(target = "email", expression = "java(profile.email())")
    @Mapping(target = "phone", expression = "java(profile.phone())")
    @Mapping(target = "organization", expression = "java(profile.organization())")
    @Mapping(target = "position", expression = "java(profile.position())")
    @Mapping(target = "branchId", expression = "java(profile.branchId())")
    @Mapping(target = "employeeNo", expression = "java(profile.employeeNo())")
    @Mapping(target = "extraAttributes", expression = "java(mapToJson(profile.extraAttributes()))")
    @Mapping(target = "createdBy", expression = "java(profile.createdBy() != null ? profile.createdBy().value() : null)")
    @Mapping(target = "updatedBy", expression = "java(profile.updatedBy() != null ? profile.updatedBy().value() : null)")
    @Mapping(target = "createTime", expression = "java(profile.createdAt())")
    @Mapping(target = "updateTime", expression = "java(profile.updatedAt())")
    @Mapping(target = "version", expression = "java(profile.version() != null ? (int) profile.version().value() : null)")
    @Mapping(target = "deleted", constant = "false")
    UserProfileDO toProfileDO(UserProfile profile);

    default User toDomain(UserDO userDO, UserProfileDO profileDO) {
        if (userDO == null) {
            return null;
        }
        UserProfile profile = toProfileDomain(profileDO);
        return User.reconstitute(
                UserId.of(userDO.getId()),
                toChannelType(userDO.getChannelType()),
                userDO.getLoginName(),
                userDO.getDisplayName(),
                toUserStatus(userDO.getStatus()),
                userDO.getLastLoginTime(),
                userDO.getLastLoginIp(),
                profile,
                toUserNo(userDO.getCreatedBy()),
                toUserNo(userDO.getUpdatedBy()),
                userDO.getCreateTime(),
                userDO.getUpdateTime(),
                toVersion(userDO.getVersion())
        );
    }

    default UserProfile toProfileDomain(UserProfileDO profileDO) {
        if (profileDO == null) {
            return null;
        }
        return UserProfile.reconstitute(
                UserId.of(profileDO.getUserId()),
                toChannelType(profileDO.getChannelType()),
                profileDO.getEmail(),
                profileDO.getPhone(),
                profileDO.getOrganization(),
                profileDO.getPosition(),
                profileDO.getBranchId(),
                profileDO.getEmployeeNo(),
                jsonToMap(profileDO.getExtraAttributes()),
                toUserNo(profileDO.getCreatedBy()),
                toUserNo(profileDO.getUpdatedBy()),
                profileDO.getCreateTime(),
                profileDO.getUpdateTime(),
                toVersion(profileDO.getVersion())
        );
    }

    @Named("toChannelType")
    default ChannelType toChannelType(String channelType) {
        return channelType != null ? ChannelType.valueOf(channelType) : null;
    }

    @Named("toUserStatus")
    default UserStatus toUserStatus(String status) {
        return status != null ? UserStatus.valueOf(status) : null;
    }

    @Named("toUserNo")
    default UserNo toUserNo(String userNo) {
        return userNo != null ? UserNo.of(userNo) : null;
    }

    @Named("toVersion")
    default Version toVersion(Integer version) {
        return version != null ? Version.of(version) : null;
    }

    default String mapToJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化扩展属性失败", e);
        }
    }

    default Map<String, String> jsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return java.util.Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化扩展属性失败", e);
        }
    }
}
