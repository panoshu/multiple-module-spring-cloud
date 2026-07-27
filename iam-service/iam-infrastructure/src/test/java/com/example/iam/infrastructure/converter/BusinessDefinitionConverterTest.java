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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link BusinessDefinitionConverter} 单元测试。
 *
 * <p>覆盖 BusinessDefinition 与 BusinessDefinitionDO + 子表(BusinessActionDO)双向映射、
 * null 输入处理、子表集合转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("BusinessDefinitionConverter 转换器测试")
class BusinessDefinitionConverterTest {

    private final BusinessDefinitionConverter converter =
            Mappers.getMapper(BusinessDefinitionConverter.class);

    private static final Long DEFINITION_ID_VALUE = 2001L;
    private static final BusinessCode BUSINESS_CODE = BusinessCode.of("ANNUITY_ESTABLISH");
    private static final String BUSINESS_NAME = "年金计划设立";
    private static final String DESCRIPTION = "企业年金计划设立业务";
    private static final boolean ACTIVE = true;
    private static final String OPERATOR = "U-ADMIN";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 1, 10, 0, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 7, 2, 11, 30, 0);
    private static final long VERSION_VALUE = 1L;

    // BusinessAction 值对象
    private static final BusinessAction ACTION_HANDLE = BusinessAction.of(Action.HANDLE, "办理业务");
    private static final BusinessAction ACTION_QUERY = BusinessAction.of(Action.QUERY, "查询业务");
    private static final BusinessAction ACTION_AUDIT = BusinessAction.of(Action.AUDIT, "审核业务");

    @Nested
    @DisplayName("toDO: BusinessDefinition -> BusinessDefinitionDO")
    class ToDOTest {

        @Test
        @DisplayName("完整字段映射:supportedActions 字段被忽略")
        void shouldMapAllFieldsToDO() {
            BusinessDefinition definition = buildDefinition();

            BusinessDefinitionDO definitionDO = converter.toDO(definition);

            assertThat(definitionDO).isNotNull();
            assertThat(definitionDO.getId()).isEqualTo(DEFINITION_ID_VALUE);
            assertThat(definitionDO.getBusinessCode()).isEqualTo(BUSINESS_CODE.value());
            assertThat(definitionDO.getBusinessName()).isEqualTo(BUSINESS_NAME);
            assertThat(definitionDO.getDescription()).isEqualTo(DESCRIPTION);
            // supportedActions 在 toDO 中 ignore = true
            assertThat(definitionDO.getSupportedActions()).isNull();
            assertThat(definitionDO.getActive()).isTrue();
            assertThat(definitionDO.getCreatedBy()).isEqualTo(OPERATOR);
            assertThat(definitionDO.getUpdatedBy()).isEqualTo(OPERATOR);
            assertThat(definitionDO.getCreateTime()).isEqualTo(CREATED_AT);
            assertThat(definitionDO.getUpdateTime()).isEqualTo(UPDATED_AT);
            assertThat(definitionDO.getVersion()).isEqualTo((int) VERSION_VALUE);
            assertThat(definitionDO.getDeleted()).isFalse();
        }

        @Test
        @DisplayName("active=false 时正确映射")
        void shouldMapInactiveDefinition() {
            BusinessDefinition definition = BusinessDefinition.reconstitute(
                    BusinessDefinitionId.of(DEFINITION_ID_VALUE), BUSINESS_CODE,
                    BUSINESS_NAME, DESCRIPTION,
                    Set.of(ACTION_HANDLE), false,
                    UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                    CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));

            BusinessDefinitionDO definitionDO = converter.toDO(definition);

            assertThat(definitionDO.getActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("toActionDO: 生成动作明细子表 DO")
    class ToActionDOTest {

        @Test
        @DisplayName("完整字段映射:id 由 ignore 标记为 null,definitionId 通过参数注入")
        void shouldMapActionDOWithInjectedId() {
            BusinessDefinition definition = buildDefinition();
            Long definitionId = DEFINITION_ID_VALUE;

            BusinessActionDO actionDO = converter.toActionDO(definition, ACTION_HANDLE, definitionId);

            assertThat(actionDO).isNotNull();
            assertThat(actionDO.getId()).isNull(); // @Mapping(target = "id", ignore = true)
            assertThat(actionDO.getDefinitionId()).isEqualTo(definitionId);
            assertThat(actionDO.getAction()).isEqualTo(Action.HANDLE.name());
            assertThat(actionDO.getDescription()).isEqualTo("办理业务");
            assertThat(actionDO.getCreatedBy()).isEqualTo(OPERATOR);
            assertThat(actionDO.getUpdatedBy()).isEqualTo(OPERATOR);
            assertThat(actionDO.getCreateTime()).isEqualTo(CREATED_AT);
            assertThat(actionDO.getUpdateTime()).isEqualTo(UPDATED_AT);
            assertThat(actionDO.getVersion()).isEqualTo((int) VERSION_VALUE);
            assertThat(actionDO.getDeleted()).isFalse();
        }

        @Test
        @DisplayName("description 为 null 的动作正确映射")
        void shouldMapActionWithNullDescription() {
            BusinessDefinition definition = buildDefinition();
            BusinessAction actionWithoutDesc = BusinessAction.of(Action.AUDIT);

            BusinessActionDO actionDO = converter.toActionDO(definition, actionWithoutDesc, DEFINITION_ID_VALUE);

            assertThat(actionDO.getAction()).isEqualTo(Action.AUDIT.name());
            assertThat(actionDO.getDescription()).isNull();
        }
    }

    @Nested
    @DisplayName("toDomain: (BusinessDefinitionDO, List<BusinessActionDO>) -> BusinessDefinition")
    class ToDomainTest {

        @Test
        @DisplayName("完整字段映射:含动作子表")
        void shouldMapToDomainWithActions() {
            BusinessDefinitionDO definitionDO = buildDefinitionDO();
            List<BusinessActionDO> actionDOs = new ArrayList<>();
            actionDOs.add(buildActionDO(Action.HANDLE, "办理业务"));
            actionDOs.add(buildActionDO(Action.QUERY, "查询业务"));
            actionDOs.add(buildActionDO(Action.AUDIT, "审核业务"));

            BusinessDefinition definition = converter.toDomain(definitionDO, actionDOs);

            assertThat(definition).isNotNull();
            assertThat(definition.id().value()).isEqualTo(DEFINITION_ID_VALUE);
            assertThat(definition.businessCode()).isEqualTo(BUSINESS_CODE);
            assertThat(definition.businessName()).isEqualTo(BUSINESS_NAME);
            assertThat(definition.description()).isEqualTo(DESCRIPTION);
            assertThat(definition.supportedActions())
                    .containsExactlyInAnyOrder(ACTION_HANDLE, ACTION_QUERY, ACTION_AUDIT);
            assertThat(definition.isActive()).isTrue();
            assertThat(definition.createdBy().value()).isEqualTo(OPERATOR);
            assertThat(definition.updatedBy().value()).isEqualTo(OPERATOR);
            assertThat(definition.createdAt()).isEqualTo(CREATED_AT);
            assertThat(definition.updatedAt()).isEqualTo(UPDATED_AT);
            assertThat(definition.version().value()).isEqualTo(VERSION_VALUE);
        }

        @Test
        @DisplayName("definitionDO 为 null 时返回 null")
        void shouldReturnNullWhenDOIsNull() {
            assertThat(converter.toDomain(null, new ArrayList<>())).isNull();
        }

        @Test
        @DisplayName("actionDOs 为 null 时按空集合处理,领域校验拒绝重建")
        void shouldThrowWhenActionListIsNull() {
            BusinessDefinitionDO definitionDO = buildDefinitionDO();

            // Converter 行为:actionDOs 为 null 时按空集合处理;
            // 但 BusinessDefinition.validateInvariants() 拒绝空 supportedActions,重建抛 IllegalStateException
            assertThatThrownBy(() -> converter.toDomain(definitionDO, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("supportedActions cannot be null or empty");
        }

        @Test
        @DisplayName("active 字段为 null 时按 false 处理")
        void shouldTreatNullActiveAsFalse() {
            BusinessDefinitionDO definitionDO = buildDefinitionDO();
            definitionDO.setActive(null);

            BusinessDefinition definition = converter.toDomain(definitionDO, List.of(buildActionDO(Action.HANDLE, "办理")));

            assertThat(definition.isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("枚举与 ID 类型转换")
    class TypeConversionTest {

        @Test
        @DisplayName("toUserNo: null 返回 null")
        void shouldReturnNullUserNoForNullString() {
            assertThat(converter.toUserNo(null)).isNull();
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
        @DisplayName("主表 toDomain(toDO(definition)) 关键字段一致")
        void shouldPreserveMainTableFieldsThroughRoundTrip() {
            BusinessDefinition original = buildDefinition();

            BusinessDefinitionDO intermediateDO = converter.toDO(original);
            // toDO 丢失 supportedActions,重建时需提供非空 actionDOs 才能通过领域校验
            List<BusinessActionDO> actionDOs = original.supportedActions().stream()
                    .map(action -> converter.toActionDO(original, action, DEFINITION_ID_VALUE))
                    .toList();
            BusinessDefinition rebuilt = converter.toDomain(intermediateDO, actionDOs);

            assertThat(rebuilt.id()).isEqualTo(original.id());
            assertThat(rebuilt.businessCode()).isEqualTo(original.businessCode());
            assertThat(rebuilt.businessName()).isEqualTo(original.businessName());
            assertThat(rebuilt.description()).isEqualTo(original.description());
            assertThat(rebuilt.isActive()).isEqualTo(original.isActive());
            assertThat(rebuilt.createdBy()).isEqualTo(original.createdBy());
            assertThat(rebuilt.updatedBy()).isEqualTo(original.updatedBy());
            assertThat(rebuilt.version()).isEqualTo(original.version());
        }

        @Test
        @DisplayName("动作子表 toActionDO 字段一致")
        void shouldPreserveActionFieldsThroughConversion() {
            BusinessDefinition definition = buildDefinition();

            BusinessActionDO actionDO = converter.toActionDO(definition, ACTION_HANDLE, DEFINITION_ID_VALUE);

            assertThat(actionDO.getDefinitionId()).isEqualTo(DEFINITION_ID_VALUE);
            assertThat(actionDO.getAction()).isEqualTo(ACTION_HANDLE.action().name());
            assertThat(actionDO.getDescription()).isEqualTo(ACTION_HANDLE.description());
        }
    }

    private BusinessDefinition buildDefinition() {
        Set<BusinessAction> actions = new LinkedHashSet<>();
        actions.add(ACTION_HANDLE);
        actions.add(ACTION_QUERY);
        actions.add(ACTION_AUDIT);
        return BusinessDefinition.reconstitute(
                BusinessDefinitionId.of(DEFINITION_ID_VALUE), BUSINESS_CODE,
                BUSINESS_NAME, DESCRIPTION,
                actions, ACTIVE,
                UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));
    }

    private BusinessDefinitionDO buildDefinitionDO() {
        BusinessDefinitionDO definitionDO = new BusinessDefinitionDO();
        definitionDO.setId(DEFINITION_ID_VALUE);
        definitionDO.setBusinessCode(BUSINESS_CODE.value());
        definitionDO.setBusinessName(BUSINESS_NAME);
        definitionDO.setDescription(DESCRIPTION);
        definitionDO.setSupportedActions(null);
        definitionDO.setActive(ACTIVE);
        definitionDO.setCreatedBy(OPERATOR);
        definitionDO.setUpdatedBy(OPERATOR);
        definitionDO.setCreateTime(CREATED_AT);
        definitionDO.setUpdateTime(UPDATED_AT);
        definitionDO.setVersion((int) VERSION_VALUE);
        definitionDO.setDeleted(false);
        return definitionDO;
    }

    private BusinessActionDO buildActionDO(Action action, String description) {
        BusinessActionDO actionDO = new BusinessActionDO();
        actionDO.setId(null);
        actionDO.setDefinitionId(DEFINITION_ID_VALUE);
        actionDO.setAction(action.name());
        actionDO.setDescription(description);
        actionDO.setCreatedBy(OPERATOR);
        actionDO.setUpdatedBy(OPERATOR);
        actionDO.setCreateTime(CREATED_AT);
        actionDO.setUpdateTime(UPDATED_AT);
        actionDO.setVersion((int) VERSION_VALUE);
        actionDO.setDeleted(false);
        return actionDO;
    }
}
