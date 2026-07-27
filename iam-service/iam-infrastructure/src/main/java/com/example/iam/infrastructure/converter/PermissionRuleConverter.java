package com.example.iam.infrastructure.converter;

import com.example.iam.domain.authorization.aggregate.root.PermissionRule;
import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.OverrideMode;
import com.example.iam.domain.authorization.aggregate.valueobject.RuleStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.SubjectType;
import com.example.iam.infrastructure.entity.PermissionRuleDO;
import com.example.iam.types.PermissionRuleId;
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
 * 权限规则聚合根转换器。
 *
 * <p>负责 {@link PermissionRule} 与 {@link PermissionRuleDO} 之间的转换。
 * {@code allowedActions} 以 JSON 数组字符串持久化(如 ["HANDLE","QUERY"])。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper(componentModel = "spring")
public interface PermissionRuleConverter {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mapping(target = "id", expression = "java(rule.id() != null ? rule.id().value() : null)")
    @Mapping(target = "ruleCode", expression = "java(rule.ruleCode())")
    @Mapping(target = "ruleName", expression = "java(rule.ruleName())")
    @Mapping(target = "subjectType", expression = "java(rule.subjectType() != null ? rule.subjectType().name() : null)")
    @Mapping(target = "subjectId", expression = "java(rule.subjectId())")
    @Mapping(target = "businessCode", expression = "java(rule.businessCode() != null ? rule.businessCode().value() : null)")
    @Mapping(target = "allowedActions", expression = "java(actionSetToJson(rule.allowedActions()))")
    @Mapping(target = "inheritToChildren", expression = "java(rule.isInheritToChildren())")
    @Mapping(target = "overrideMode", expression = "java(rule.overrideMode() != null ? rule.overrideMode().name() : null)")
    @Mapping(target = "priority", expression = "java(rule.priority())")
    @Mapping(target = "status", expression = "java(rule.status() != null ? rule.status().name() : null)")
    @Mapping(target = "effectiveAt", expression = "java(rule.effectiveAt())")
    @Mapping(target = "expireAt", expression = "java(rule.expireAt())")
    @Mapping(target = "createdBy", expression = "java(rule.createdBy() != null ? rule.createdBy().value() : null)")
    @Mapping(target = "updatedBy", expression = "java(rule.updatedBy() != null ? rule.updatedBy().value() : null)")
    @Mapping(target = "createTime", expression = "java(rule.createdAt())")
    @Mapping(target = "updateTime", expression = "java(rule.updatedAt())")
    @Mapping(target = "version", expression = "java(rule.version() != null ? (int) rule.version().value() : null)")
    @Mapping(target = "deleted", constant = "false")
    PermissionRuleDO toDO(PermissionRule rule);

    default PermissionRule toDomain(PermissionRuleDO ruleDO) {
        if (ruleDO == null) {
            return null;
        }
        return PermissionRule.reconstitute(
                PermissionRuleId.of(ruleDO.getId()),
                ruleDO.getRuleCode(),
                ruleDO.getRuleName(),
                toSubjectType(ruleDO.getSubjectType()),
                ruleDO.getSubjectId(),
                BusinessCode.of(ruleDO.getBusinessCode()),
                jsonToActionSet(ruleDO.getAllowedActions()),
                Boolean.TRUE.equals(ruleDO.getInheritToChildren()),
                toOverrideMode(ruleDO.getOverrideMode()),
                ruleDO.getPriority(),
                toRuleStatus(ruleDO.getStatus()),
                ruleDO.getEffectiveAt(),
                ruleDO.getExpireAt(),
                toUserNo(ruleDO.getCreatedBy()),
                toUserNo(ruleDO.getUpdatedBy()),
                ruleDO.getCreateTime(),
                ruleDO.getUpdateTime(),
                toVersion(ruleDO.getVersion())
        );
    }

    @Named("toSubjectType")
    default SubjectType toSubjectType(String subjectType) {
        return subjectType != null ? SubjectType.valueOf(subjectType) : null;
    }

    @Named("toOverrideMode")
    default OverrideMode toOverrideMode(String overrideMode) {
        return overrideMode != null ? OverrideMode.valueOf(overrideMode) : null;
    }

    @Named("toRuleStatus")
    default RuleStatus toRuleStatus(String status) {
        return status != null ? RuleStatus.valueOf(status) : null;
    }

    @Named("toUserNo")
    default UserNo toUserNo(String userNo) {
        return userNo != null ? UserNo.of(userNo) : null;
    }

    @Named("toVersion")
    default Version toVersion(Integer version) {
        return version != null ? Version.of(version) : null;
    }

    default String actionSetToJson(Set<Action> actions) {
        if (actions == null || actions.isEmpty()) {
            return null;
        }
        try {
            Set<String> names = actions.stream().map(Action::name).collect(Collectors.toSet());
            return OBJECT_MAPPER.writeValueAsString(names);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("序列化动作集合失败", e);
        }
    }

    default Set<Action> jsonToActionSet(String json) {
        if (json == null || json.isBlank()) {
            return java.util.Collections.emptySet();
        }
        try {
            Set<String> names = OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
            return names.stream().map(Action::valueOf).collect(Collectors.toSet());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("反序列化动作集合失败", e);
        }
    }
}
