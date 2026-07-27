package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authorization.aggregate.root.PermissionRule;
import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.OverrideMode;
import com.example.iam.domain.authorization.aggregate.valueobject.RuleStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.SubjectType;
import com.example.iam.domain.authorization.repository.PermissionRuleRepository;
import com.example.iam.infrastructure.IamInfrastructureTestApplication;
import com.example.iam.types.PermissionRuleId;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PermissionRuleRepositoryImpl} 集成测试。
 *
 * <p>验证权限规则聚合根的 CRUD、按规则编码查询、按主体查询活动规则、按状态查询等场景。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("PermissionRuleRepositoryImpl 集成测试")
@SpringBootTest(classes = IamInfrastructureTestApplication.class)
@ActiveProfiles("test")
@Transactional
class PermissionRuleRepositoryImplTest {

    @Autowired
    private PermissionRuleRepository ruleRepository;

    private static final Long RULE_ID_VALUE = 50001L;
    private static final String RULE_CODE = "RULE-001";
    private static final String RULE_NAME = "年金设立-客户级";
    private static final SubjectType SUBJECT_TYPE = SubjectType.CUSTOMER;
    private static final String SUBJECT_ID = "C-001";
    private static final BusinessCode BUSINESS_CODE = BusinessCode.of("ANNUITY_ESTABLISH");
    private static final Set<Action> ALLOWED_ACTIONS = Set.of(Action.HANDLE, Action.QUERY);
    private static final OverrideMode OVERRIDE_MODE = OverrideMode.ADD;
    private static final UserNo OPERATOR = UserNo.of("U-ADMIN-001");

    @Nested
    @DisplayName("save + load: 新建与读取")
    class SaveAndLoadTest {

        @Test
        @DisplayName("新建权限规则后能通过 ID 加载,关键字段一致")
        void shouldSaveNewRuleAndLoadById() {
            PermissionRule rule = PermissionRule.create(
                    PermissionRuleId.of(RULE_ID_VALUE), RULE_CODE, RULE_NAME,
                    SUBJECT_TYPE, SUBJECT_ID, BUSINESS_CODE, ALLOWED_ACTIONS,
                    false, OVERRIDE_MODE, null,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);

            ruleRepository.save(rule);

            Optional<PermissionRule> loaded = ruleRepository.load(PermissionRuleId.of(RULE_ID_VALUE));

            assertThat(loaded).isPresent();
            PermissionRule actual = loaded.get();
            assertThat(actual.id().value()).isEqualTo(RULE_ID_VALUE);
            assertThat(actual.ruleCode()).isEqualTo(RULE_CODE);
            assertThat(actual.ruleName()).isEqualTo(RULE_NAME);
            assertThat(actual.subjectType()).isEqualTo(SUBJECT_TYPE);
            assertThat(actual.subjectId()).isEqualTo(SUBJECT_ID);
            assertThat(actual.businessCode()).isEqualTo(BUSINESS_CODE);
            assertThat(actual.allowedActions()).containsExactlyInAnyOrderElementsOf(ALLOWED_ACTIONS);
            assertThat(actual.overrideMode()).isEqualTo(OVERRIDE_MODE);
            assertThat(actual.status()).isEqualTo(RuleStatus.ACTIVE);
        }

        @Test
        @DisplayName("load 不存在的 ID 返回 empty")
        void shouldReturnEmptyWhenLoadNonexistentId() {
            Optional<PermissionRule> loaded = ruleRepository.load(PermissionRuleId.of(999999L));

            assertThat(loaded).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByRuleCode: 按规则编码查询")
    class FindByRuleCodeTest {

        @Test
        @DisplayName("按 ruleCode 命中已存在规则")
        void shouldFindByRuleCode() {
            PermissionRule rule = PermissionRule.create(
                    PermissionRuleId.of(RULE_ID_VALUE), RULE_CODE, RULE_NAME,
                    SUBJECT_TYPE, SUBJECT_ID, BUSINESS_CODE, ALLOWED_ACTIONS,
                    false, OVERRIDE_MODE, null,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            ruleRepository.save(rule);

            Optional<PermissionRule> found = ruleRepository.findByRuleCode(RULE_CODE);

            assertThat(found).isPresent();
            assertThat(found.get().ruleCode()).isEqualTo(RULE_CODE);
        }

        @Test
        @DisplayName("existsByRuleCode: 存在返回 true,不存在返回 false")
        void shouldCheckExistenceByRuleCode() {
            PermissionRule rule = PermissionRule.create(
                    PermissionRuleId.of(RULE_ID_VALUE), RULE_CODE, RULE_NAME,
                    SUBJECT_TYPE, SUBJECT_ID, BUSINESS_CODE, ALLOWED_ACTIONS,
                    false, OVERRIDE_MODE, null,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            ruleRepository.save(rule);

            assertThat(ruleRepository.existsByRuleCode(RULE_CODE)).isTrue();
            assertThat(ruleRepository.existsByRuleCode("NONEXISTENT")).isFalse();
        }
    }

    @Nested
    @DisplayName("findBySubject: 按主体查询活动规则")
    class FindBySubjectTest {

        @Test
        @DisplayName("返回指定主体下 ACTIVE 且生效的规则")
        void shouldFindActiveRulesBySubject() {
            PermissionRule rule = PermissionRule.create(
                    PermissionRuleId.of(RULE_ID_VALUE), RULE_CODE, RULE_NAME,
                    SUBJECT_TYPE, SUBJECT_ID, BUSINESS_CODE, ALLOWED_ACTIONS,
                    false, OVERRIDE_MODE, null,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            ruleRepository.save(rule);

            List<PermissionRule> rules = ruleRepository.findBySubject(SUBJECT_TYPE, SUBJECT_ID);

            assertThat(rules).hasSize(1);
            assertThat(rules.get(0).subjectId()).isEqualTo(SUBJECT_ID);
            assertThat(rules.get(0).status()).isEqualTo(RuleStatus.ACTIVE);
        }

        @Test
        @DisplayName("已禁用的规则不被 findBySubject 返回")
        void shouldNotReturnDisabledRules() {
            PermissionRule rule = PermissionRule.create(
                    PermissionRuleId.of(RULE_ID_VALUE), RULE_CODE, RULE_NAME,
                    SUBJECT_TYPE, SUBJECT_ID, BUSINESS_CODE, ALLOWED_ACTIONS,
                    false, OVERRIDE_MODE, null,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            rule.disable(OPERATOR);
            ruleRepository.save(rule);

            List<PermissionRule> rules = ruleRepository.findBySubject(SUBJECT_TYPE, SUBJECT_ID);

            assertThat(rules).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByStatus: 按状态查询")
    class FindByStatusTest {

        @Test
        @DisplayName("返回指定状态的所有规则")
        void shouldFindByStatus() {
            PermissionRule rule = PermissionRule.create(
                    PermissionRuleId.of(RULE_ID_VALUE), RULE_CODE, RULE_NAME,
                    SUBJECT_TYPE, SUBJECT_ID, BUSINESS_CODE, ALLOWED_ACTIONS,
                    false, OVERRIDE_MODE, null,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            ruleRepository.save(rule);

            List<PermissionRule> activeRules = ruleRepository.findByStatus(RuleStatus.ACTIVE);

            assertThat(activeRules).hasSize(1);
            assertThat(activeRules.get(0).status()).isEqualTo(RuleStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("delete: 软删除")
    class DeleteTest {

        @Test
        @DisplayName("delete 后 load 返回 empty(软删除生效)")
        void shouldSoftDeleteRule() {
            PermissionRule rule = PermissionRule.create(
                    PermissionRuleId.of(RULE_ID_VALUE), RULE_CODE, RULE_NAME,
                    SUBJECT_TYPE, SUBJECT_ID, BUSINESS_CODE, ALLOWED_ACTIONS,
                    false, OVERRIDE_MODE, null,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            ruleRepository.save(rule);

            ruleRepository.delete(rule);

            assertThat(ruleRepository.load(PermissionRuleId.of(RULE_ID_VALUE))).isEmpty();
            assertThat(ruleRepository.existsByRuleCode(RULE_CODE)).isFalse();
        }

        @Test
        @DisplayName("deleteById 后 load 返回 empty")
        void shouldSoftDeleteById() {
            PermissionRule rule = PermissionRule.create(
                    PermissionRuleId.of(RULE_ID_VALUE), RULE_CODE, RULE_NAME,
                    SUBJECT_TYPE, SUBJECT_ID, BUSINESS_CODE, ALLOWED_ACTIONS,
                    false, OVERRIDE_MODE, null,
                    LocalDateTime.now().minusMinutes(1), null, OPERATOR);
            ruleRepository.save(rule);

            ruleRepository.deleteById(PermissionRuleId.of(RULE_ID_VALUE));

            assertThat(ruleRepository.load(PermissionRuleId.of(RULE_ID_VALUE))).isEmpty();
        }

        @Test
        @DisplayName("delete null 规则不抛异常")
        void shouldNotThrowWhenDeleteNull() {
            ruleRepository.delete(null);
            ruleRepository.deleteById(null);
        }
    }
}
