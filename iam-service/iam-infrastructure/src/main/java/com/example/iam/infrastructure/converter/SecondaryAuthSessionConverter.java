package com.example.iam.infrastructure.converter;

import com.example.iam.domain.authentication.aggregate.root.SecondaryAuthSession;
import com.example.iam.domain.authentication.aggregate.valueobject.SecondaryAuthStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.PermissionCode;
import com.example.iam.infrastructure.entity.SecondaryAuthSessionDO;
import com.example.iam.types.SecondaryAuthSessionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 二次授权会话聚合根转换器。
 *
 * <p>负责 {@link SecondaryAuthSession} 与 {@link SecondaryAuthSessionDO} 之间的转换。
 * {@code permissionSnapshot} 以 JSON 数组字符串持久化(如 ["ANNUITY_ESTABLISH.HANDLE"])。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper(componentModel = "spring")
public interface SecondaryAuthSessionConverter {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mapping(target = "id", expression = "java(session.id() != null ? session.id().value() : null)")
    @Mapping(target = "tellerId", expression = "java(session.tellerId())")
    @Mapping(target = "approverId", expression = "java(session.approverId())")
    @Mapping(target = "customerNo", expression = "java(session.customerNo())")
    @Mapping(target = "planId", expression = "java(session.planId())")
    @Mapping(target = "permissionSnapshot", expression = "java(permissionSetToJson(session.permissionSnapshot()))")
    @Mapping(target = "status", expression = "java(session.status() != null ? session.status().name() : null)")
    @Mapping(target = "initiatedAt", expression = "java(session.initiatedAt())")
    @Mapping(target = "authorizedAt", expression = "java(session.authorizedAt())")
    @Mapping(target = "expireAt", expression = "java(session.expireAt())")
    @Mapping(target = "revokeReason", expression = "java(session.revokeReason())")
    @Mapping(target = "createdBy", expression = "java(session.createdBy() != null ? session.createdBy().value() : null)")
    @Mapping(target = "updatedBy", expression = "java(session.updatedBy() != null ? session.updatedBy().value() : null)")
    @Mapping(target = "createTime", expression = "java(session.createdAt())")
    @Mapping(target = "updateTime", expression = "java(session.updatedAt())")
    @Mapping(target = "version", expression = "java(session.version() != null ? (int) session.version().value() : null)")
    @Mapping(target = "deleted", constant = "false")
    SecondaryAuthSessionDO toDO(SecondaryAuthSession session);

    default SecondaryAuthSession toDomain(SecondaryAuthSessionDO sessionDO) {
        if (sessionDO == null) {
            return null;
        }
        return SecondaryAuthSession.reconstitute(
                SecondaryAuthSessionId.of(sessionDO.getId()),
                sessionDO.getTellerId(),
                sessionDO.getApproverId(),
                sessionDO.getCustomerNo(),
                sessionDO.getPlanId(),
                jsonToPermissionSet(sessionDO.getPermissionSnapshot()),
                toSecondaryAuthStatus(sessionDO.getStatus()),
                sessionDO.getInitiatedAt(),
                sessionDO.getAuthorizedAt(),
                sessionDO.getExpireAt(),
                sessionDO.getRevokeReason(),
                toUserNo(sessionDO.getCreatedBy()),
                toUserNo(sessionDO.getUpdatedBy()),
                sessionDO.getCreateTime(),
                sessionDO.getUpdateTime(),
                toVersion(sessionDO.getVersion())
        );
    }

    @Named("toSecondaryAuthStatus")
    default SecondaryAuthStatus toSecondaryAuthStatus(String status) {
        return status != null ? SecondaryAuthStatus.valueOf(status) : null;
    }

    @Named("toUserNo")
    default UserNo toUserNo(String userNo) {
        return userNo != null ? UserNo.of(userNo) : null;
    }

    @Named("toVersion")
    default Version toVersion(Integer version) {
        return version != null ? Version.of(version) : null;
    }

    default String permissionSetToJson(Set<PermissionCode> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }
        try {
            Set<String> codes = permissions.stream()
                    .map(PermissionCode::value)
                    .collect(Collectors.toSet());
            return OBJECT_MAPPER.writeValueAsString(codes);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化权限快照失败", e);
        }
    }

    default Set<PermissionCode> jsonToPermissionSet(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Set<String> codes = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            return codes.stream().map(PermissionCode::of).collect(Collectors.toSet());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化权限快照失败", e);
        }
    }
}
