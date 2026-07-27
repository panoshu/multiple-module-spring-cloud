package com.example.iam.infrastructure.converter;

import com.example.iam.domain.authorization.aggregate.root.RouteRule;
import com.example.iam.domain.authorization.aggregate.valueobject.RouteCheckType;
import com.example.iam.infrastructure.entity.RouteRuleDO;
import com.example.iam.types.RouteRuleId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * 路由权限规则聚合根转换器。
 *
 * <p>负责 {@link RouteRule} 与 {@link RouteRuleDO} 之间的转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper(componentModel = "spring")
public interface RouteRuleConverter {

    @Mapping(target = "id", expression = "java(rule.id() != null ? rule.id().value() : null)")
    @Mapping(target = "routePattern", expression = "java(rule.routePattern())")
    @Mapping(target = "checkType", expression = "java(rule.checkType() != null ? rule.checkType().name() : null)")
    @Mapping(target = "checkValue", expression = "java(rule.checkValue())")
    @Mapping(target = "description", expression = "java(rule.description())")
    @Mapping(target = "enabled", expression = "java(rule.isEnabled())")
    @Mapping(target = "priority", expression = "java(rule.priority())")
    @Mapping(target = "createdBy", expression = "java(rule.createdBy() != null ? rule.createdBy().value() : null)")
    @Mapping(target = "updatedBy", expression = "java(rule.updatedBy() != null ? rule.updatedBy().value() : null)")
    @Mapping(target = "createTime", expression = "java(rule.createdAt())")
    @Mapping(target = "updateTime", expression = "java(rule.updatedAt())")
    @Mapping(target = "version", expression = "java(rule.version() != null ? (int) rule.version().value() : null)")
    @Mapping(target = "deleted", constant = "false")
    RouteRuleDO toDO(RouteRule rule);

    default RouteRule toDomain(RouteRuleDO ruleDO) {
        if (ruleDO == null) {
            return null;
        }
        return RouteRule.reconstitute(
                RouteRuleId.of(ruleDO.getId()),
                ruleDO.getRoutePattern(),
                toRouteCheckType(ruleDO.getCheckType()),
                ruleDO.getCheckValue(),
                ruleDO.getDescription(),
                ruleDO.getPriority(),
                Boolean.TRUE.equals(ruleDO.getEnabled()),
                toUserNo(ruleDO.getCreatedBy()),
                toUserNo(ruleDO.getUpdatedBy()),
                ruleDO.getCreateTime(),
                ruleDO.getUpdateTime(),
                toVersion(ruleDO.getVersion())
        );
    }

    @Named("toRouteCheckType")
    default RouteCheckType toRouteCheckType(String checkType) {
        return checkType != null ? RouteCheckType.valueOf(checkType) : null;
    }

    @Named("toUserNo")
    default UserNo toUserNo(String userNo) {
        return userNo != null ? UserNo.of(userNo) : null;
    }

    @Named("toVersion")
    default Version toVersion(Integer version) {
        return version != null ? Version.of(version) : null;
    }
}
