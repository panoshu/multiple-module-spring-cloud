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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link PermissionRuleConverter} 单元测试。
 *
 * <p>覆盖 PermissionRule 与 PermissionRuleDO 双向映射、
 * allowedActions JSON 序列化、枚举/ID 类型转换、null 输入处理。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("PermissionRuleConverter 转换器测试")
class PermissionRuleConverterTest {

    private final PermissionRuleConverter converter =
            Mappers.getMapper(PermissionRuleConverter.class);

    private static final Long RULE_ID_VALUE = 6001L;
    private static final String RULE_CODE = "R-001";
    private static final String RULE_NAME = "客户级年金设立规则";
    private static final SubjectType SUBJECT_TYPE = SubjectType.CUSTOMER;
    private static final String SUBJECT_ID = "C-001";
    private static final BusinessCode BUSINESS_CODE = BusinessCode.of("ANNUITY_ESTABLISH");
    private static final boolean INHERIT_TO_CHILDREN = true;
    private static final OverrideMode OVERRIDE_MODE = OverrideMode.ADD;
    private static final Integer PRIORITY = 10;
    private static final RuleStatus STATUS = RuleStatus.ACTIVE;
    private static final LocalDateTime EFFECTIVE_AT = LocalDateTime.of(2026, 7, 1, 0, 0, 0);
    private static final LocalDateTime EXPIRE_AT = LocalDateTime.of(2027, 1, 1, 0, 0, 0);
    private static final String OPERATOR = "U-ADMIN";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 1, 10, 0, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 7, 2, 11, 30, 0);
    private static final long VERSION_VALUE = 3L;

    private static final Set<Action> ALLOWED_ACTIONS = EnumSet.of(Action.HANDLE, Action.QUERY);

    @Nested
    @DisplayName("toDO: PermissionRule -> PermissionRuleDO")
    class ToDOTest {

        @Test
        @DisplayName("完整字段映射:allowedActions 序列化为 JSON")
        void shouldMapAllFieldsToDO() {
            PermissionRule rule = buildRule();

            PermissionRuleDO ruleDO = converter.toDO(rule);

            assertThat(ruleDO).isNotNull();
            assertThat(ruleDO.getId()).isEqualTo(RULE_ID_VALUE);
            assertThat(ruleDO.getRuleCode()).isEqualTo(RULE_CODE);
            assertThat(ruleDO.getRuleName()).isEqualTo(RULE_NAME);
            assertThat(ruleDO.getSubjectType()).isEqualTo(SUBJECT_TYPE.name());
            assertThat(ruleDO.getSubjectId()).isEqualTo(SUBJECT_ID);
            assertThat(ruleDO.getBusinessCode()).isEqualTo(BUSINESS_CODE.value());
            assertThat(ruleDO.getAllowedActions())
                    .contains("HANDLE")
                    .contains("QUERY");
            assertThat(ruleDO.getInheritToChildren()).isTrue();
            assertThat(ruleDO.getOverrideMode()).isEqualTo(OVERRIDE_MODE.name());
            assertThat(ruleDO.getPriority()).isEqualTo(PRIORITY);
            assertThat(ruleDO.getStatus()).isEqualTo(STATUS.name());
            assertThat(ruleDO.getEffectiveAt()).isEqualTo(EFFECTIVE_AT);
            assertThat(ruleDO.getExpireAt()).isEqualTo(EXPIRE_AT);
            assertThat(ruleDO.getCreatedBy()).isEqualTo(OPERATOR);
            assertThat(ruleDO.getUpdatedBy()).isEqualTo(OPERATOR);
            assertThat(ruleDO.getCreateTime()).isEqualTo(CREATED_AT);
            assertThat(ruleDO.getUpdateTime()).isEqualTo(UPDATED_AT);
            assertThat(ruleDO.getVersion()).isEqualTo((int) VERSION_VALUE);
            assertThat(ruleDO.getDeleted()).isFalse();
        }

        @Test
        @DisplayName("priority 为 null 时正确映射")
        void shouldMapNullPriority() {
            PermissionRule rule = PermissionRule.reconstitute(
                    PermissionRuleId.of(RULE_ID_VALUE), RULE_CODE, RULE_NAME,
                    SUBJECT_TYPE, SUBJECT_ID, BUSINESS_CODE, ALLOWED_ACTIONS,
                    INHERIT_TO_CHILDREN, OVERRIDE_MODE, null,
                    STATUS, EFFECTIVE_AT, EXPIRE_AT,
                    UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                    CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));

            PermissionRuleDO ruleDO = converter.toDO(rule);

            assertThat(ruleDO.getPriority()).isNull();
        }

        @Test
        @DisplayName("DISABLED 状态正确映射")
        void shouldMapDisabledStatus() {
            PermissionRule rule = PermissionRule.reconstitute(
                    PermissionRuleId.of(RULE_ID_VALUE), RULE_CODE, RULE_NAME,
                    SUBJECT_TYPE, SUBJECT_ID, BUSINESS_CODE, ALLOWED_ACTIONS,
                    INHERIT_TO_CHILDREN, OVERRIDE_MODE, PRIORITY,
                    RuleStatus.DISABLED, EFFECTIVE_AT, EXPIRE_AT,
                    UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                    CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));

            PermissionRuleDO ruleDO = converter.toDO(rule);

            assertThat(ruleDO.getStatus()).isEqualTo("DISABLED");
        }
    }

    @Nested
    @DisplayName("toDomain: PermissionRuleDO -> PermissionRule")
    class ToDomainTest {

        @Test
        @DisplayName("完整字段映射:allowedActions 反序列化为 Set<Action>")
        void shouldMapAllFieldsToDomain() {
            PermissionRuleDO ruleDO = buildRuleDO("[\"HANDLE\",\"QUERY\"]");

            PermissionRule rule = converter.toDomain(ruleDO);

            assertThat(rule).isNotNull();
            assertThat(rule.id().value()).isEqualTo(RULE_ID_VALUE);
            assertThat(rule.ruleCode()).isEqualTo(RULE_CODE);
            assertThat(rule.ruleName()).isEqualTo(RULE_NAME);
            assertThat(rule.subjectType()).isEqualTo(SUBJECT_TYPE);
            assertThat(rule.subjectId()).isEqualTo(SUBJECT_ID);
            assertThat(rule.businessCode()).isEqualTo(BUSINESS_CODE);
            assertThat(rule.allowedActions()).containsExactlyInAnyOrder(Action.HANDLE, Action.QUERY);
            assertThat(rule.isInheritToChildren()).isTrue();
            assertThat(rule.overrideMode()).isEqualTo(OVERRIDE_MODE);
            assertThat(rule.priority()).isEqualTo(PRIORITY);
            assertThat(rule.status()).isEqualTo(STATUS);
            assertThat(rule.effectiveAt()).isEqualTo(EFFECTIVE_AT);
            assertThat(rule.expireAt()).isEqualTo(EXPIRE_AT);
            assertThat(rule.createdBy().value()).isEqualTo(OPERATOR);
            assertThat(rule.updatedBy().value()).isEqualTo(OPERATOR);
            assertThat(rule.createdAt()).isEqualTo(CREATED_AT);
            assertThat(rule.updatedAt()).isEqualTo(UPDATED_AT);
            assertThat(rule.version().value()).isEqualTo(VERSION_VALUE);
        }

        @Test
        @DisplayName("DO 为 null 时返回 null")
        void shouldReturnNullWhenDOIsNull() {
            assertThat(converter.toDomain(null)).isNull();
        }

        @Test
        @DisplayName("allowedActions 为 null 时反序列化为空 Set,领域校验拒绝重建")
        void shouldThrowWhenActionsJsonIsNull() {
            PermissionRuleDO ruleDO = buildRuleDO(null);

            // Converter 行为:jsonToActionSet(null) 返回空 Set;
            // 但 PermissionRule.validateInvariants() 拒绝空集合,重建抛 IllegalStateException
            assertThatThrownBy(() -> converter.toDomain(ruleDO))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("allowedActions cannot be null or empty");
        }

        @Test
        @DisplayName("inheritToChildren 为 null 时按 false 处理")
        void shouldTreatNullInheritAsFalse() {
            PermissionRuleDO ruleDO = buildRuleDO("[\"HANDLE\"]");
            ruleDO.setInheritToChildren(null);

            PermissionRule rule = converter.toDomain(ruleDO);

            assertThat(rule.isInheritToChildren()).isFalse();
        }
    }

    @Nested
    @DisplayName("JSON 辅助方法")
    class JsonHelperTest {

        @Test
        @DisplayName("actionSetToJson: null 返回 null")
        void shouldReturnNullForNullSet() {
            assertThat(converter.actionSetToJson(null)).isNull();
        }

        @Test
        @DisplayName("actionSetToJson: 空 Set 返回 null")
        void shouldReturnNullForEmptySet() {
            assertThat(converter.actionSetToJson(EnumSet.noneOf(Action.class))).isNull();
        }

        @Test
        @DisplayName("jsonToActionSet: null 返回空 Set")
        void shouldReturnEmptySetForNullJson() {
            assertThat(converter.jsonToActionSet(null)).isEmpty();
        }

        @Test
        @DisplayName("jsonToActionSet: 空白字符串返回空 Set")
        void shouldReturnEmptySetForBlankJson() {
            assertThat(converter.jsonToActionSet("  ")).isEmpty();
        }

        @Test
        @DisplayName("双向转换:Set -> JSON -> Set 保持一致")
        void shouldRoundTripSetAndJson() {
            Set<Action> original = EnumSet.of(Action.HANDLE, Action.AUDIT);

            String json = converter.actionSetToJson(original);
            Set<Action> rebuilt = converter.jsonToActionSet(json);

            assertThat(rebuilt).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("枚举与 ID 类型转换")
    class TypeConversionTest {

        @Test
        @DisplayName("toSubjectType: null 返回 null")
        void shouldReturnNullSubjectTypeForNullString() {
            assertThat(converter.toSubjectType(null)).isNull();
        }

        @Test
        @DisplayName("toOverrideMode: null 返回 null")
        void shouldReturnNullOverrideModeForNullString() {
            assertThat(converter.toOverrideMode(null)).isNull();
        }

        @Test
        @DisplayName("toRuleStatus: null 返回 null")
        void shouldReturnNullRuleStatusForNullString() {
            assertThat(converter.toRuleStatus(null)).isNull();
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
        @DisplayName("toDomain(toDO(rule)) 关键字段一致")
        void shouldPreserveKeyFieldsThroughRoundTrip() {
            PermissionRule original = buildRule();

            PermissionRuleDO intermediateDO = converter.toDO(original);
            PermissionRule rebuilt = converter.toDomain(intermediateDO);

            assertThat(rebuilt.id()).isEqualTo(original.id());
            assertThat(rebuilt.ruleCode()).isEqualTo(original.ruleCode());
            assertThat(rebuilt.ruleName()).isEqualTo(original.ruleName());
            assertThat(rebuilt.subjectType()).isEqualTo(original.subjectType());
            assertThat(rebuilt.subjectId()).isEqualTo(original.subjectId());
            assertThat(rebuilt.businessCode()).isEqualTo(original.businessCode());
            assertThat(rebuilt.allowedActions()).isEqualTo(original.allowedActions());
            assertThat(rebuilt.isInheritToChildren()).isEqualTo(original.isInheritToChildren());
            assertThat(rebuilt.overrideMode()).isEqualTo(original.overrideMode());
            assertThat(rebuilt.priority()).isEqualTo(original.priority());
            assertThat(rebuilt.status()).isEqualTo(original.status());
            assertThat(rebuilt.effectiveAt()).isEqualTo(original.effectiveAt());
            assertThat(rebuilt.expireAt()).isEqualTo(original.expireAt());
            assertThat(rebuilt.createdBy()).isEqualTo(original.createdBy());
            assertThat(rebuilt.updatedBy()).isEqualTo(original.updatedBy());
            assertThat(rebuilt.version()).isEqualTo(original.version());
        }
    }

    private PermissionRule buildRule() {
        return PermissionRule.reconstitute(
                PermissionRuleId.of(RULE_ID_VALUE), RULE_CODE, RULE_NAME,
                SUBJECT_TYPE, SUBJECT_ID, BUSINESS_CODE, ALLOWED_ACTIONS,
                INHERIT_TO_CHILDREN, OVERRIDE_MODE, PRIORITY,
                STATUS, EFFECTIVE_AT, EXPIRE_AT,
                UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));
    }

    private PermissionRuleDO buildRuleDO(String allowedActionsJson) {
        PermissionRuleDO ruleDO = new PermissionRuleDO();
        ruleDO.setId(RULE_ID_VALUE);
        ruleDO.setRuleCode(RULE_CODE);
        ruleDO.setRuleName(RULE_NAME);
        ruleDO.setSubjectType(SUBJECT_TYPE.name());
        ruleDO.setSubjectId(SUBJECT_ID);
        ruleDO.setBusinessCode(BUSINESS_CODE.value());
        ruleDO.setAllowedActions(allowedActionsJson);
        ruleDO.setInheritToChildren(INHERIT_TO_CHILDREN);
        ruleDO.setOverrideMode(OVERRIDE_MODE.name());
        ruleDO.setPriority(PRIORITY);
        ruleDO.setStatus(STATUS.name());
        ruleDO.setEffectiveAt(EFFECTIVE_AT);
        ruleDO.setExpireAt(EXPIRE_AT);
        ruleDO.setCreatedBy(OPERATOR);
        ruleDO.setUpdatedBy(OPERATOR);
        ruleDO.setCreateTime(CREATED_AT);
        ruleDO.setUpdateTime(UPDATED_AT);
        ruleDO.setVersion((int) VERSION_VALUE);
        ruleDO.setDeleted(false);
        return ruleDO;
    }
}
