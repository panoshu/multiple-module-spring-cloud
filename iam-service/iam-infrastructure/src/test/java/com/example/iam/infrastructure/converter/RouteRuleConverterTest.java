package com.example.iam.infrastructure.converter;

import com.example.iam.domain.authorization.aggregate.root.RouteRule;
import com.example.iam.domain.authorization.aggregate.valueobject.RouteCheckType;
import com.example.iam.infrastructure.entity.RouteRuleDO;
import com.example.iam.types.RouteRuleId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RouteRuleConverter} 单元测试。
 *
 * <p>覆盖 toDO / toDomain 双向映射、null 输入处理、字段一致性校验。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("RouteRuleConverter 转换器测试")
class RouteRuleConverterTest {

    private final RouteRuleConverter converter = Mappers.getMapper(RouteRuleConverter.class);

    private static final Long RULE_ID = 1001L;
    private static final String ROUTE_PATTERN = "/internet/**";
    private static final RouteCheckType CHECK_TYPE = RouteCheckType.PERMISSION;
    private static final String CHECK_VALUE = "ANNUITY_ESTABLISH.HANDLE";
    private static final String DESCRIPTION = "互联网渠道路由规则";
    private static final int PRIORITY = 100;
    private static final boolean ENABLED = true;
    private static final String OPERATOR = "U-ADMIN";
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 7, 1, 10, 0, 0);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 7, 2, 11, 30, 0);
    private static final long VERSION_VALUE = 3L;

    @Nested
    @DisplayName("toDO: RouteRule -> RouteRuleDO")
    class ToDOTest {

        @Test
        @DisplayName("完整字段映射:所有字段值一致")
        void shouldMapAllFieldsToDO() {
            RouteRule rule = buildRouteRule();

            RouteRuleDO ruleDO = converter.toDO(rule);

            assertThat(ruleDO).isNotNull();
            assertThat(ruleDO.getId()).isEqualTo(RULE_ID);
            assertThat(ruleDO.getRoutePattern()).isEqualTo(ROUTE_PATTERN);
            assertThat(ruleDO.getCheckType()).isEqualTo(CHECK_TYPE.name());
            assertThat(ruleDO.getCheckValue()).isEqualTo(CHECK_VALUE);
            assertThat(ruleDO.getDescription()).isEqualTo(DESCRIPTION);
            assertThat(ruleDO.getEnabled()).isEqualTo(ENABLED);
            assertThat(ruleDO.getPriority()).isEqualTo(PRIORITY);
            assertThat(ruleDO.getCreatedBy()).isEqualTo(OPERATOR);
            assertThat(ruleDO.getUpdatedBy()).isEqualTo(OPERATOR);
            assertThat(ruleDO.getCreateTime()).isEqualTo(CREATED_AT);
            assertThat(ruleDO.getUpdateTime()).isEqualTo(UPDATED_AT);
            assertThat(ruleDO.getVersion()).isEqualTo((int) VERSION_VALUE);
            assertThat(ruleDO.getDeleted()).isFalse();
        }

        @Test
        @DisplayName("SKIP 类型 checkValue 为 null 时仍可正确映射")
        void shouldMapSkipTypeWithNullCheckValue() {
            RouteRule rule = RouteRule.reconstitute(
                    RouteRuleId.of(2002L),
                    "/public/**",
                    RouteCheckType.SKIP,
                    null,
                    "白名单跳过校验",
                    50,
                    true,
                    UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                    CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));

            RouteRuleDO ruleDO = converter.toDO(rule);

            assertThat(ruleDO.getCheckType()).isEqualTo("SKIP");
            assertThat(ruleDO.getCheckValue()).isNull();
        }

        @Test
        @DisplayName("enabled=false 时正确映射")
        void shouldMapDisabledRule() {
            RouteRule rule = RouteRule.reconstitute(
                    RouteRuleId.of(RULE_ID),
                    ROUTE_PATTERN,
                    CHECK_TYPE, CHECK_VALUE, DESCRIPTION,
                    PRIORITY, false,
                    UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                    CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));

            RouteRuleDO ruleDO = converter.toDO(rule);

            assertThat(ruleDO.getEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("toDomain: RouteRuleDO -> RouteRule")
    class ToDomainTest {

        @Test
        @DisplayName("完整字段映射:所有字段值一致")
        void shouldMapAllFieldsToDomain() {
            RouteRuleDO ruleDO = buildRouteRuleDO();

            RouteRule rule = converter.toDomain(ruleDO);

            assertThat(rule).isNotNull();
            assertThat(rule.id().value()).isEqualTo(RULE_ID);
            assertThat(rule.routePattern()).isEqualTo(ROUTE_PATTERN);
            assertThat(rule.checkType()).isEqualTo(CHECK_TYPE);
            assertThat(rule.checkValue()).isEqualTo(CHECK_VALUE);
            assertThat(rule.description()).isEqualTo(DESCRIPTION);
            assertThat(rule.isEnabled()).isEqualTo(ENABLED);
            assertThat(rule.priority()).isEqualTo(PRIORITY);
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
        @DisplayName("enabled 字段为 null 时按 false 处理")
        void shouldTreatNullEnabledAsFalse() {
            RouteRuleDO ruleDO = buildRouteRuleDO();
            ruleDO.setEnabled(null);

            RouteRule rule = converter.toDomain(ruleDO);

            assertThat(rule.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("双向转换一致性")
    class RoundTripTest {

        @Test
        @DisplayName("toDomain(toDO(entity)) 关键字段一致")
        void shouldPreserveKeyFieldsThroughRoundTrip() {
            RouteRule original = buildRouteRule();

            RouteRuleDO intermediateDO = converter.toDO(original);
            RouteRule rebuilt = converter.toDomain(intermediateDO);

            assertThat(rebuilt.id()).isEqualTo(original.id());
            assertThat(rebuilt.routePattern()).isEqualTo(original.routePattern());
            assertThat(rebuilt.checkType()).isEqualTo(original.checkType());
            assertThat(rebuilt.checkValue()).isEqualTo(original.checkValue());
            assertThat(rebuilt.description()).isEqualTo(original.description());
            assertThat(rebuilt.isEnabled()).isEqualTo(original.isEnabled());
            assertThat(rebuilt.priority()).isEqualTo(original.priority());
            assertThat(rebuilt.createdBy()).isEqualTo(original.createdBy());
            assertThat(rebuilt.updatedBy()).isEqualTo(original.updatedBy());
            assertThat(rebuilt.createdAt()).isEqualTo(original.createdAt());
            assertThat(rebuilt.updatedAt()).isEqualTo(original.updatedAt());
            assertThat(rebuilt.version()).isEqualTo(original.version());
        }
    }

    private RouteRule buildRouteRule() {
        return RouteRule.reconstitute(
                RouteRuleId.of(RULE_ID),
                ROUTE_PATTERN,
                CHECK_TYPE, CHECK_VALUE, DESCRIPTION,
                PRIORITY, ENABLED,
                UserNo.of(OPERATOR), UserNo.of(OPERATOR),
                CREATED_AT, UPDATED_AT, Version.of(VERSION_VALUE));
    }

    private RouteRuleDO buildRouteRuleDO() {
        RouteRuleDO ruleDO = new RouteRuleDO();
        ruleDO.setId(RULE_ID);
        ruleDO.setRoutePattern(ROUTE_PATTERN);
        ruleDO.setCheckType(CHECK_TYPE.name());
        ruleDO.setCheckValue(CHECK_VALUE);
        ruleDO.setDescription(DESCRIPTION);
        ruleDO.setEnabled(ENABLED);
        ruleDO.setPriority(PRIORITY);
        ruleDO.setCreatedBy(OPERATOR);
        ruleDO.setUpdatedBy(OPERATOR);
        ruleDO.setCreateTime(CREATED_AT);
        ruleDO.setUpdateTime(UPDATED_AT);
        ruleDO.setVersion((int) VERSION_VALUE);
        ruleDO.setDeleted(false);
        return ruleDO;
    }
}
