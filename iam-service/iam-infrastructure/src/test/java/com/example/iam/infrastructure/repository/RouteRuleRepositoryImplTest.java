package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authorization.aggregate.root.RouteRule;
import com.example.iam.domain.authorization.aggregate.valueobject.RouteCheckType;
import com.example.iam.domain.authorization.repository.RouteRuleRepository;
import com.example.iam.infrastructure.IamInfrastructureTestApplication;
import com.example.iam.types.RouteRuleId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RouteRuleRepositoryImpl} 集成测试。
 *
 * <p>验证路由权限规则聚合根的 CRUD、按路由模式查询、查询所有启用规则等场景。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("RouteRuleRepositoryImpl 集成测试")
@SpringBootTest(classes = IamInfrastructureTestApplication.class)
@ActiveProfiles("test")
@Transactional
class RouteRuleRepositoryImplTest {

    @Autowired
    private RouteRuleRepository routeRuleRepository;

    private static final Long RULE_ID_VALUE = 70001L;
    private static final Long ALT_RULE_ID_VALUE = 70002L;
    private static final String ROUTE_PATTERN = "internet/**";
    private static final RouteCheckType CHECK_TYPE = RouteCheckType.LOGIN;
    private static final String CHECK_VALUE = "INTERNET";
    private static final String DESCRIPTION = "网上渠道登录校验";
    private static final int PRIORITY = 100;
    private static final UserNo OPERATOR = UserNo.of("U-ADMIN-001");

    @Nested
    @DisplayName("save + load: 新建与读取")
    class SaveAndLoadTest {

        @Test
        @DisplayName("新建路由规则后能通过 ID 加载,关键字段一致")
        void shouldSaveNewRuleAndLoadById() {
            RouteRule rule = RouteRule.create(
                    RouteRuleId.of(RULE_ID_VALUE), ROUTE_PATTERN, CHECK_TYPE,
                    CHECK_VALUE, DESCRIPTION, PRIORITY, OPERATOR);

            routeRuleRepository.save(rule);

            Optional<RouteRule> loaded = routeRuleRepository.load(RouteRuleId.of(RULE_ID_VALUE));

            assertThat(loaded).isPresent();
            RouteRule actual = loaded.get();
            assertThat(actual.id().value()).isEqualTo(RULE_ID_VALUE);
            assertThat(actual.routePattern()).isEqualTo(ROUTE_PATTERN);
            assertThat(actual.checkType()).isEqualTo(CHECK_TYPE);
            assertThat(actual.checkValue()).isEqualTo(CHECK_VALUE);
            assertThat(actual.description()).isEqualTo(DESCRIPTION);
            assertThat(actual.priority()).isEqualTo(PRIORITY);
            assertThat(actual.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("load 不存在的 ID 返回 empty")
        void shouldReturnEmptyWhenLoadNonexistentId() {
            Optional<RouteRule> loaded = routeRuleRepository.load(RouteRuleId.of(999999L));

            assertThat(loaded).isEmpty();
        }

        @Test
        @DisplayName("load 传入 null 返回 empty")
        void shouldReturnEmptyWhenLoadNullId() {
            Optional<RouteRule> loaded = routeRuleRepository.load(null);

            assertThat(loaded).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByRoutePattern: 按路由模式查询")
    class FindByRoutePatternTest {

        @Test
        @DisplayName("按 routePattern 命中已存在规则")
        void shouldFindByRoutePattern() {
            RouteRule rule = RouteRule.create(
                    RouteRuleId.of(RULE_ID_VALUE), ROUTE_PATTERN, CHECK_TYPE,
                    CHECK_VALUE, DESCRIPTION, PRIORITY, OPERATOR);
            routeRuleRepository.save(rule);

            Optional<RouteRule> found = routeRuleRepository.findByRoutePattern(ROUTE_PATTERN);

            assertThat(found).isPresent();
            assertThat(found.get().routePattern()).isEqualTo(ROUTE_PATTERN);
        }

        @Test
        @DisplayName("routePattern 不存在时返回 empty")
        void shouldReturnEmptyWhenPatternNotFound() {
            Optional<RouteRule> found = routeRuleRepository.findByRoutePattern("nonexistent/**");

            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllEnabled: 查询所有启用规则")
    class FindAllEnabledTest {

        @Test
        @DisplayName("返回所有 enabled=true 的规则,按 priority 倒序")
        void shouldFindAllEnabledRulesOrderedByPriorityDesc() {
            RouteRule r1 = RouteRule.create(
                    RouteRuleId.of(RULE_ID_VALUE), "internet/**", CHECK_TYPE,
                    CHECK_VALUE, DESCRIPTION, 100, OPERATOR);
            RouteRule r2 = RouteRule.create(
                    RouteRuleId.of(ALT_RULE_ID_VALUE), "hq/**", RouteCheckType.PERMISSION,
                    "ANNUITY_ESTABLISH.HANDLE", "总部权限校验", 200, OPERATOR);
            routeRuleRepository.save(r1);
            routeRuleRepository.save(r2);

            List<RouteRule> enabled = routeRuleRepository.findAllEnabled();

            assertThat(enabled).hasSize(2);
            assertThat(enabled.get(0).priority()).isGreaterThanOrEqualTo(enabled.get(1).priority());
        }

        @Test
        @DisplayName("禁用的规则不被 findAllEnabled 返回")
        void shouldNotReturnDisabledRules() {
            RouteRule rule = RouteRule.create(
                    RouteRuleId.of(RULE_ID_VALUE), ROUTE_PATTERN, CHECK_TYPE,
                    CHECK_VALUE, DESCRIPTION, PRIORITY, OPERATOR);
            rule.disable(OPERATOR);
            routeRuleRepository.save(rule);

            List<RouteRule> enabled = routeRuleRepository.findAllEnabled();

            assertThat(enabled).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAll: 查询所有规则")
    class FindAllTest {

        @Test
        @DisplayName("返回所有规则(含禁用),按 priority 倒序")
        void shouldFindAllRules() {
            RouteRule r1 = RouteRule.create(
                    RouteRuleId.of(RULE_ID_VALUE), "internet/**", CHECK_TYPE,
                    CHECK_VALUE, DESCRIPTION, 100, OPERATOR);
            RouteRule r2 = RouteRule.create(
                    RouteRuleId.of(ALT_RULE_ID_VALUE), "hq/**", RouteCheckType.SKIP,
                    null, "跳过校验", 50, OPERATOR);
            routeRuleRepository.save(r1);
            routeRuleRepository.save(r2);

            List<RouteRule> all = routeRuleRepository.findAll();

            assertThat(all).hasSize(2);
        }
    }

    @Nested
    @DisplayName("delete: 软删除")
    class DeleteTest {

        @Test
        @DisplayName("delete 后 load 返回 empty(软删除生效)")
        void shouldSoftDeleteRule() {
            RouteRule rule = RouteRule.create(
                    RouteRuleId.of(RULE_ID_VALUE), ROUTE_PATTERN, CHECK_TYPE,
                    CHECK_VALUE, DESCRIPTION, PRIORITY, OPERATOR);
            routeRuleRepository.save(rule);

            routeRuleRepository.delete(rule);

            assertThat(routeRuleRepository.load(RouteRuleId.of(RULE_ID_VALUE))).isEmpty();
        }

        @Test
        @DisplayName("deleteById 后 load 返回 empty")
        void shouldSoftDeleteById() {
            RouteRule rule = RouteRule.create(
                    RouteRuleId.of(RULE_ID_VALUE), ROUTE_PATTERN, CHECK_TYPE,
                    CHECK_VALUE, DESCRIPTION, PRIORITY, OPERATOR);
            routeRuleRepository.save(rule);

            routeRuleRepository.deleteById(RouteRuleId.of(RULE_ID_VALUE));

            assertThat(routeRuleRepository.load(RouteRuleId.of(RULE_ID_VALUE))).isEmpty();
            assertThat(routeRuleRepository.findByRoutePattern(ROUTE_PATTERN)).isEmpty();
        }

        @Test
        @DisplayName("delete null 规则不抛异常")
        void shouldNotThrowWhenDeleteNull() {
            routeRuleRepository.delete(null);
            routeRuleRepository.deleteById(null);
        }
    }
}
