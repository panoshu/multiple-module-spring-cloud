package com.example.iam.infrastructure.repository;

import com.example.iam.domain.authorization.aggregate.root.BusinessDefinition;
import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessAction;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.repository.BusinessDefinitionRepository;
import com.example.iam.infrastructure.IamInfrastructureTestApplication;
import com.example.iam.types.BusinessDefinitionId;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BusinessDefinitionRepositoryImpl} 集成测试。
 *
 * <p>验证业务定义聚合根(含 BusinessAction 子表)的 CRUD、按业务编码查询、按启用状态查询、
 * 子表全量替换等场景。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("BusinessDefinitionRepositoryImpl 集成测试")
@SpringBootTest(classes = IamInfrastructureTestApplication.class)
@ActiveProfiles("test")
@Transactional
class BusinessDefinitionRepositoryImplTest {

    @Autowired
    private BusinessDefinitionRepository definitionRepository;

    private static final Long DEFINITION_ID_VALUE = 80001L;
    private static final BusinessCode BUSINESS_CODE = BusinessCode.of("ANNUITY_ESTABLISH");
    private static final String BUSINESS_NAME = "年金计划设立";
    private static final String DESCRIPTION = "年金计划设立业务";
    private static final Set<BusinessAction> SUPPORTED_ACTIONS = Set.of(
            BusinessAction.of(Action.HANDLE, "办理"),
            BusinessAction.of(Action.QUERY, "查询"),
            BusinessAction.of(Action.AUDIT, "审核"));
    private static final UserNo OPERATOR = UserNo.of("U-ADMIN-001");

    @Nested
    @DisplayName("save + load: 新建与读取")
    class SaveAndLoadTest {

        @Test
        @DisplayName("新建业务定义后能通过 ID 加载,含子表动作")
        void shouldSaveNewDefinitionAndLoadById() {
            BusinessDefinition definition = BusinessDefinition.create(
                    BusinessDefinitionId.of(DEFINITION_ID_VALUE), BUSINESS_CODE,
                    BUSINESS_NAME, DESCRIPTION, SUPPORTED_ACTIONS, OPERATOR);

            definitionRepository.save(definition);

            Optional<BusinessDefinition> loaded = definitionRepository
                    .load(BusinessDefinitionId.of(DEFINITION_ID_VALUE));

            assertThat(loaded).isPresent();
            BusinessDefinition actual = loaded.get();
            assertThat(actual.id().value()).isEqualTo(DEFINITION_ID_VALUE);
            assertThat(actual.businessCode()).isEqualTo(BUSINESS_CODE);
            assertThat(actual.businessName()).isEqualTo(BUSINESS_NAME);
            assertThat(actual.description()).isEqualTo(DESCRIPTION);
            assertThat(actual.isActive()).isTrue();
            assertThat(actual.supportedActions())
                    .extracting(BusinessAction::action)
                    .containsExactlyInAnyOrder(Action.HANDLE, Action.QUERY, Action.AUDIT);
        }

        @Test
        @DisplayName("load 不存在的 ID 返回 empty")
        void shouldReturnEmptyWhenLoadNonexistentId() {
            Optional<BusinessDefinition> loaded = definitionRepository
                    .load(BusinessDefinitionId.of(999999L));

            assertThat(loaded).isEmpty();
        }

        @Test
        @DisplayName("load 传入 null 返回 empty")
        void shouldReturnEmptyWhenLoadNullId() {
            Optional<BusinessDefinition> loaded = definitionRepository.load(null);

            assertThat(loaded).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByBusinessCode: 按业务编码查询")
    class FindByBusinessCodeTest {

        @Test
        @DisplayName("按 businessCode 命中已存在业务定义")
        void shouldFindByBusinessCode() {
            BusinessDefinition definition = BusinessDefinition.create(
                    BusinessDefinitionId.of(DEFINITION_ID_VALUE), BUSINESS_CODE,
                    BUSINESS_NAME, DESCRIPTION, SUPPORTED_ACTIONS, OPERATOR);
            definitionRepository.save(definition);

            Optional<BusinessDefinition> found = definitionRepository.findByBusinessCode(BUSINESS_CODE);

            assertThat(found).isPresent();
            assertThat(found.get().businessCode()).isEqualTo(BUSINESS_CODE);
            assertThat(found.get().supportedActions()).hasSize(SUPPORTED_ACTIONS.size());
        }

        @Test
        @DisplayName("existsByBusinessCode: 存在返回 true,不存在返回 false")
        void shouldCheckExistenceByBusinessCode() {
            BusinessDefinition definition = BusinessDefinition.create(
                    BusinessDefinitionId.of(DEFINITION_ID_VALUE), BUSINESS_CODE,
                    BUSINESS_NAME, DESCRIPTION, SUPPORTED_ACTIONS, OPERATOR);
            definitionRepository.save(definition);

            assertThat(definitionRepository.existsByBusinessCode(BUSINESS_CODE)).isTrue();
            assertThat(definitionRepository.existsByBusinessCode(BusinessCode.of("NONEXISTENT")))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("findByActive: 按启用状态查询")
    class FindByActiveTest {

        @Test
        @DisplayName("返回 active=true 的业务定义")
        void shouldFindByActiveTrue() {
            BusinessDefinition definition = BusinessDefinition.create(
                    BusinessDefinitionId.of(DEFINITION_ID_VALUE), BUSINESS_CODE,
                    BUSINESS_NAME, DESCRIPTION, SUPPORTED_ACTIONS, OPERATOR);
            definitionRepository.save(definition);

            List<BusinessDefinition> actives = definitionRepository.findByActive(true);

            assertThat(actives).hasSize(1);
            assertThat(actives.get(0).isActive()).isTrue();
        }

        @Test
        @DisplayName("禁用的业务定义出现在 findByActive(false) 结果中")
        void shouldFindByActiveFalse() {
            BusinessDefinition definition = BusinessDefinition.create(
                    BusinessDefinitionId.of(DEFINITION_ID_VALUE), BUSINESS_CODE,
                    BUSINESS_NAME, DESCRIPTION, SUPPORTED_ACTIONS, OPERATOR);
            definition.disable(OPERATOR);
            definitionRepository.save(definition);

            List<BusinessDefinition> inactives = definitionRepository.findByActive(false);

            assertThat(inactives).hasSize(1);
            assertThat(inactives.get(0).isActive()).isFalse();
        }
    }

    @Nested
    @DisplayName("findAll / loadAll: 全量加载")
    class FindAllTest {

        @Test
        @DisplayName("加载所有业务定义,数量与保存一致")
        void shouldFindAllDefinitions() {
            BusinessDefinition d1 = BusinessDefinition.create(
                    BusinessDefinitionId.of(DEFINITION_ID_VALUE), BUSINESS_CODE,
                    BUSINESS_NAME, DESCRIPTION, SUPPORTED_ACTIONS, OPERATOR);
            BusinessDefinition d2 = BusinessDefinition.create(
                    BusinessDefinitionId.of(80002L), BusinessCode.of("ANNUITY_PAYMENT"),
                    "年金支付", "年金支付业务",
                    Set.of(BusinessAction.of(Action.HANDLE), BusinessAction.of(Action.QUERY)),
                    OPERATOR);
            definitionRepository.save(d1);
            definitionRepository.save(d2);

            List<BusinessDefinition> all = definitionRepository.findAll();

            assertThat(all).hasSize(2);
            assertThat(all).extracting(d -> d.businessCode().value())
                    .containsExactlyInAnyOrder("ANNUITY_ESTABLISH", "ANNUITY_PAYMENT");
        }
    }

    @Nested
    @DisplayName("delete: 软删除(含子表)")
    class DeleteTest {

        @Test
        @DisplayName("delete 后 load 返回 empty(含子表清理)")
        void shouldSoftDeleteDefinitionAndChildren() {
            BusinessDefinition definition = BusinessDefinition.create(
                    BusinessDefinitionId.of(DEFINITION_ID_VALUE), BUSINESS_CODE,
                    BUSINESS_NAME, DESCRIPTION, SUPPORTED_ACTIONS, OPERATOR);
            definitionRepository.save(definition);

            definitionRepository.delete(definition);

            assertThat(definitionRepository.load(BusinessDefinitionId.of(DEFINITION_ID_VALUE)))
                    .isEmpty();
        }

        @Test
        @DisplayName("deleteById 后 load 返回 empty")
        void shouldSoftDeleteById() {
            BusinessDefinition definition = BusinessDefinition.create(
                    BusinessDefinitionId.of(DEFINITION_ID_VALUE), BUSINESS_CODE,
                    BUSINESS_NAME, DESCRIPTION, SUPPORTED_ACTIONS, OPERATOR);
            definitionRepository.save(definition);

            definitionRepository.deleteById(BusinessDefinitionId.of(DEFINITION_ID_VALUE));

            assertThat(definitionRepository.load(BusinessDefinitionId.of(DEFINITION_ID_VALUE)))
                    .isEmpty();
            assertThat(definitionRepository.existsByBusinessCode(BUSINESS_CODE)).isFalse();
        }

        @Test
        @DisplayName("delete null 业务定义不抛异常")
        void shouldNotThrowWhenDeleteNull() {
            definitionRepository.delete(null);
            definitionRepository.deleteById(null);
        }
    }
}
