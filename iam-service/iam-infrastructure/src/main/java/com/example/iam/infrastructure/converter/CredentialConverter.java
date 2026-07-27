package com.example.iam.infrastructure.converter;

import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.infrastructure.entity.CredentialDO;
import com.example.iam.types.CredentialId;
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
 * 凭据聚合根转换器。
 *
 * <p>负责 {@link Credential} 与 {@link CredentialDO} 之间的转换。
 * {@code auxData} 以 JSON 字符串持久化,通过 Jackson 进行序列化/反序列化。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper(componentModel = "spring")
public interface CredentialConverter {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mapping(target = "id", expression = "java(credential.id() != null ? credential.id().value() : null)")
    @Mapping(target = "ownerType", expression = "java(credential.ownerType())")
    @Mapping(target = "ownerId", expression = "java(credential.ownerId())")
    @Mapping(target = "credentialType", expression = "java(credential.credentialType() != null ? credential.credentialType().name() : null)")
    @Mapping(target = "secretHash", expression = "java(credential.secretHash())")
    @Mapping(target = "salt", expression = "java(credential.salt())")
    @Mapping(target = "auxData", expression = "java(mapToJson(credential.auxData()))")
    @Mapping(target = "status", expression = "java(credential.status() != null ? credential.status().name() : null)")
    @Mapping(target = "expireTime", expression = "java(credential.expireTime())")
    @Mapping(target = "createdBy", expression = "java(credential.createdBy() != null ? credential.createdBy().value() : null)")
    @Mapping(target = "updatedBy", expression = "java(credential.updatedBy() != null ? credential.updatedBy().value() : null)")
    @Mapping(target = "createTime", expression = "java(credential.createdAt())")
    @Mapping(target = "updateTime", expression = "java(credential.updatedAt())")
    @Mapping(target = "version", expression = "java(credential.version() != null ? (int) credential.version().value() : null)")
    @Mapping(target = "deleted", constant = "false")
    CredentialDO toDO(Credential credential);

    default Credential toDomain(CredentialDO credentialDO) {
        if (credentialDO == null) {
            return null;
        }
        return Credential.reconstitute(
                CredentialId.of(credentialDO.getId()),
                credentialDO.getOwnerType(),
                credentialDO.getOwnerId(),
                toCredentialType(credentialDO.getCredentialType()),
                credentialDO.getSecretHash(),
                credentialDO.getSalt(),
                jsonToMap(credentialDO.getAuxData()),
                toCredentialStatus(credentialDO.getStatus()),
                credentialDO.getExpireTime(),
                toUserNo(credentialDO.getCreatedBy()),
                toUserNo(credentialDO.getUpdatedBy()),
                credentialDO.getCreateTime(),
                credentialDO.getUpdateTime(),
                toVersion(credentialDO.getVersion())
        );
    }

    @Named("toCredentialType")
    default CredentialType toCredentialType(String credentialType) {
        return credentialType != null ? CredentialType.valueOf(credentialType) : null;
    }

    @Named("toCredentialStatus")
    default CredentialStatus toCredentialStatus(String status) {
        return status != null ? CredentialStatus.valueOf(status) : null;
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
            throw new RuntimeException("序列化辅助数据失败", e);
        }
    }

    default Map<String, String> jsonToMap(String json) {
        if (json == null || json.isBlank()) {
            return java.util.Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化辅助数据失败", e);
        }
    }
}
