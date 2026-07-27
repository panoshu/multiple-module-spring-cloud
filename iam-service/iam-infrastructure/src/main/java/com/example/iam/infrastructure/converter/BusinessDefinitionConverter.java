package com.example.iam.infrastructure.converter;

import com.example.iam.domain.authorization.aggregate.root.BusinessDefinition;
import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessAction;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.infrastructure.entity.BusinessActionDO;
import com.example.iam.infrastructure.entity.BusinessDefinitionDO;
import com.example.iam.types.BusinessDefinitionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 业务定义聚合根转换器。
 *
 * <p>负责 {@link BusinessDefinition} 与 {@link BusinessDefinitionDO}+子表
 * {@link BusinessActionDO} 之间的转换。子表记录每个支持的动作及其描述。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Mapper(componentModel = "spring")
public interface BusinessDefinitionConverter {

    @Mapping(target = "id", expression = "java(definition.id() != null ? definition.id().value() : null)")
    @Mapping(target = "businessCode", expression = "java(definition.businessCode() != null ? definition.businessCode().value() : null)")
    @Mapping(target = "businessName", expression = "java(definition.businessName())")
    @Mapping(target = "description", expression = "java(definition.description())")
    @Mapping(target = "supportedActions", ignore = true)
    @Mapping(target = "active", expression = "java(definition.isActive())")
    @Mapping(target = "createdBy", expression = "java(definition.createdBy() != null ? definition.createdBy().value() : null)")
    @Mapping(target = "updatedBy", expression = "java(definition.updatedBy() != null ? definition.updatedBy().value() : null)")
    @Mapping(target = "createTime", expression = "java(definition.createdAt())")
    @Mapping(target = "updateTime", expression = "java(definition.updatedAt())")
    @Mapping(target = "version", expression = "java(definition.version() != null ? (int) definition.version().value() : null)")
    @Mapping(target = "deleted", constant = "false")
    BusinessDefinitionDO toDO(BusinessDefinition definition);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "definitionId", expression = "java(definitionId)")
    @Mapping(target = "action", expression = "java(businessAction.action() != null ? businessAction.action().name() : null)")
    @Mapping(target = "description", expression = "java(businessAction.description())")
    @Mapping(target = "createdBy", expression = "java(definition.createdBy() != null ? definition.createdBy().value() : null)")
    @Mapping(target = "updatedBy", expression = "java(definition.updatedBy() != null ? definition.updatedBy().value() : null)")
    @Mapping(target = "createTime", expression = "java(definition.createdAt())")
    @Mapping(target = "updateTime", expression = "java(definition.updatedAt())")
    @Mapping(target = "version", expression = "java(definition.version() != null ? (int) definition.version().value() : null)")
    @Mapping(target = "deleted", constant = "false")
    BusinessActionDO toActionDO(BusinessDefinition definition, BusinessAction businessAction, Long definitionId);

    default BusinessDefinition toDomain(BusinessDefinitionDO definitionDO, List<BusinessActionDO> actionDOs) {
        if (definitionDO == null) {
            return null;
        }
        Set<BusinessAction> actions = new HashSet<>();
        if (actionDOs != null) {
            for (BusinessActionDO actionDO : actionDOs) {
                actions.add(BusinessAction.of(
                        Action.valueOf(actionDO.getAction()),
                        actionDO.getDescription()
                ));
            }
        }
        return BusinessDefinition.reconstitute(
                BusinessDefinitionId.of(definitionDO.getId()),
                BusinessCode.of(definitionDO.getBusinessCode()),
                definitionDO.getBusinessName(),
                definitionDO.getDescription(),
                actions,
                Boolean.TRUE.equals(definitionDO.getActive()),
                toUserNo(definitionDO.getCreatedBy()),
                toUserNo(definitionDO.getUpdatedBy()),
                definitionDO.getCreateTime(),
                definitionDO.getUpdateTime(),
                toVersion(definitionDO.getVersion())
        );
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
