package com.example.iam.domain.authorization.aggregate.root;

import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessAction;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.types.BusinessDefinitionId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BusinessDefinition 聚合根行为测试。
 *
 * <p>覆盖工厂方法、enable/disable 状态机、动作支持判断、权限校验、
 * reconstitute 重建、字段不可变性与空值/业务规则校验。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@DisplayName("BusinessDefinition 聚合根行为")
class BusinessDefinitionTest {

  private static final UserNo OPERATOR = UserNo.of("U-ADMIN");
  private static final BusinessDefinitionId DEFINITION_ID = BusinessDefinitionId.of(8001L);
  private static final BusinessCode BUSINESS_CODE = BusinessCode.of("ANNUITY_ESTABLISH");
  private static final BusinessAction HANDLE_ACTION = BusinessAction.of(Action.HANDLE, "办理");
  private static final BusinessAction QUERY_ACTION = BusinessAction.of(Action.QUERY, "查询");
  private static final BusinessAction AUDIT_ACTION = BusinessAction.of(Action.AUDIT, "审核");

  @Nested
  @DisplayName("create 工厂方法")
  class CreateTest {

    @Test
    @DisplayName("create 初始化启用状态")
    void create_initializesActiveState() {
      LocalDateTime before = LocalDateTime.now();

      BusinessDefinition definition = createDefaultDefinition();

      assertThat(definition.id()).isEqualTo(DEFINITION_ID);
      assertThat(definition.businessCode()).isEqualTo(BUSINESS_CODE);
      assertThat(definition.businessName()).isEqualTo("年金计划设立");
      assertThat(definition.description()).isEqualTo("年金计划设立业务");
      assertThat(definition.supportedActions()).containsExactly(HANDLE_ACTION);
      assertThat(definition.isActive()).isTrue();
      assertThat(definition.createdBy()).isEqualTo(OPERATOR);
      assertThat(definition.updatedBy()).isEqualTo(OPERATOR);
      assertThat(definition.createdAt()).isAfterOrEqualTo(before);
      assertThat(definition.version()).isEqualTo(Version.initial());
    }

    @Test
    @DisplayName("create 不注册领域事件")
    void create_doesNotRegisterEvents() {
      BusinessDefinition definition = createDefaultDefinition();

      assertThat(definition.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("create 在 description 为 null 时接受")
    void create_acceptsNullDescription() {
      BusinessDefinition definition = BusinessDefinition.create(
          DEFINITION_ID, BUSINESS_CODE, "年金计划设立", null,
          Set.of(HANDLE_ACTION), OPERATOR
      );

      assertThat(definition.description()).isNull();
    }

    @Test
    @DisplayName("create 拒绝空 businessCode")
    void create_rejectsNullBusinessCode() {
      assertThatThrownBy(() -> BusinessDefinition.create(
          DEFINITION_ID, null, "年金计划设立", "描述",
          Set.of(HANDLE_ACTION), OPERATOR
      )).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("create 拒绝空 businessName")
    void create_rejectsBlankBusinessName() {
      assertThatThrownBy(() -> BusinessDefinition.create(
          DEFINITION_ID, BUSINESS_CODE, "", "描述",
          Set.of(HANDLE_ACTION), OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝空 supportedActions 集合")
    void create_rejectsEmptyActions() {
      assertThatThrownBy(() -> BusinessDefinition.create(
          DEFINITION_ID, BUSINESS_CODE, "年金计划设立", "描述",
          Set.of(), OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝空 supportedActions 引用")
    void create_rejectsNullActions() {
      assertThatThrownBy(() -> BusinessDefinition.create(
          DEFINITION_ID, BUSINESS_CODE, "年金计划设立", "描述",
          null, OPERATOR
      )).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("create 拒绝重复的业务动作")
    void create_rejectsDuplicateActions() {
      BusinessAction duplicate1 = BusinessAction.of(Action.HANDLE, "办理1");
      BusinessAction duplicate2 = BusinessAction.of(Action.HANDLE, "办理2");

      assertThatThrownBy(() -> BusinessDefinition.create(
          DEFINITION_ID, BUSINESS_CODE, "年金计划设立", "描述",
          Set.of(duplicate1, duplicate2), OPERATOR
      )).isInstanceOf(DomainException.class);
    }
  }

  @Nested
  @DisplayName("enable/disable 状态机")
  class StateMachineTest {

    @Test
    @DisplayName("disable 将启用状态置为禁用")
    void disable_changesActiveToInactive() {
      BusinessDefinition definition = createDefaultDefinition();
      Version initialVersion = definition.version();

      definition.disable(OPERATOR);

      assertThat(definition.isActive()).isFalse();
      assertThat(definition.version()).isEqualTo(initialVersion.next());
      assertThat(definition.updatedBy()).isEqualTo(OPERATOR);
    }

    @Test
    @DisplayName("disable 在已禁用状态时抛 DomainException")
    void disable_throwsWhenAlreadyDisabled() {
      BusinessDefinition definition = createDefaultDefinition();
      definition.disable(OPERATOR);

      assertThatThrownBy(() -> definition.disable(OPERATOR))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("enable 将禁用状态恢复为启用")
    void enable_changesInactiveToActive() {
      BusinessDefinition definition = createDefaultDefinition();
      definition.disable(OPERATOR);

      definition.enable(OPERATOR);

      assertThat(definition.isActive()).isTrue();
    }

    @Test
    @DisplayName("enable 在已启用状态时抛 DomainException")
    void enable_throwsWhenAlreadyActive() {
      BusinessDefinition definition = createDefaultDefinition();

      assertThatThrownBy(() -> definition.enable(OPERATOR))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("disable 与 enable 可循环转换")
    void disableAndEnable_canCycle() {
      BusinessDefinition definition = createDefaultDefinition();

      definition.disable(OPERATOR);
      definition.enable(OPERATOR);
      definition.disable(OPERATOR);

      assertThat(definition.isActive()).isFalse();
    }
  }

  @Nested
  @DisplayName("supports 动作支持判断")
  class SupportsTest {

    @Test
    @DisplayName("supports 在动作被支持时返回 true")
    void supports_returnsTrueWhenActionSupported() {
      BusinessDefinition definition = BusinessDefinition.create(
          DEFINITION_ID, BUSINESS_CODE, "年金计划设立", "描述",
          Set.of(HANDLE_ACTION, QUERY_ACTION), OPERATOR
      );

      assertThat(definition.supports(Action.HANDLE)).isTrue();
      assertThat(definition.supports(Action.QUERY)).isTrue();
    }

    @Test
    @DisplayName("supports 在动作不被支持时返回 false")
    void supports_returnsFalseWhenActionNotSupported() {
      BusinessDefinition definition = createDefaultDefinition();

      assertThat(definition.supports(Action.AUDIT)).isFalse();
    }

    @Test
    @DisplayName("supports 拒绝空 action")
    void supports_rejectsNullAction() {
      BusinessDefinition definition = createDefaultDefinition();

      assertThatThrownBy(() -> definition.supports(null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("validatePermission 业务权限校验")
  class ValidatePermissionTest {

    @Test
    @DisplayName("validatePermission 在业务编码匹配且动作支持时通过")
    void validatePermission_passesWhenCodeAndActionMatch() {
      BusinessDefinition definition = BusinessDefinition.create(
          DEFINITION_ID, BUSINESS_CODE, "年金计划设立", "描述",
          Set.of(HANDLE_ACTION, QUERY_ACTION), OPERATOR
      );

      definition.validatePermission(BUSINESS_CODE, Action.HANDLE);

      assertThat(definition.isActive()).isTrue();
    }

    @Test
    @DisplayName("validatePermission 在业务编码不匹配时抛 DomainException")
    void validatePermission_throwsWhenCodeMismatch() {
      BusinessDefinition definition = createDefaultDefinition();
      BusinessCode otherCode = BusinessCode.of("ANNUITY_PAYMENT");

      assertThatThrownBy(() -> definition.validatePermission(otherCode, Action.HANDLE))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("validatePermission 在动作不被支持时抛 DomainException")
    void validatePermission_throwsWhenActionNotSupported() {
      BusinessDefinition definition = createDefaultDefinition();

      assertThatThrownBy(() -> definition.validatePermission(BUSINESS_CODE, Action.AUDIT))
          .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("validatePermission 拒绝空 businessCode")
    void validatePermission_rejectsNullBusinessCode() {
      BusinessDefinition definition = createDefaultDefinition();

      assertThatThrownBy(() -> definition.validatePermission(null, Action.HANDLE))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("validatePermission 拒绝空 action")
    void validatePermission_rejectsNullAction() {
      BusinessDefinition definition = createDefaultDefinition();

      assertThatThrownBy(() -> definition.validatePermission(BUSINESS_CODE, null))
          .isInstanceOf(NullPointerException.class);
    }
  }

  @Nested
  @DisplayName("reconstitute 数据库重建")
  class ReconstituteTest {

    @Test
    @DisplayName("reconstitute 恢复完整聚合状态(含禁用状态)")
    void reconstitute_restoresFullAggregate() {
      LocalDateTime createdAt = LocalDateTime.of(2026, 7, 1, 9, 0);
      LocalDateTime updatedAt = LocalDateTime.of(2026, 7, 26, 14, 30);
      Version version = Version.of(5);

      BusinessDefinition definition = BusinessDefinition.reconstitute(
          DEFINITION_ID, BUSINESS_CODE, "年金计划设立", "年金计划设立业务",
          Set.of(HANDLE_ACTION, QUERY_ACTION, AUDIT_ACTION), false,
          OPERATOR, OPERATOR, createdAt, updatedAt, version
      );

      assertThat(definition.businessCode()).isEqualTo(BUSINESS_CODE);
      assertThat(definition.businessName()).isEqualTo("年金计划设立");
      assertThat(definition.description()).isEqualTo("年金计划设立业务");
      assertThat(definition.supportedActions())
          .containsExactlyInAnyOrder(HANDLE_ACTION, QUERY_ACTION, AUDIT_ACTION);
      assertThat(definition.isActive()).isFalse();
      assertThat(definition.createdBy()).isEqualTo(OPERATOR);
      assertThat(definition.updatedBy()).isEqualTo(OPERATOR);
      assertThat(definition.createdAt()).isEqualTo(createdAt);
      assertThat(definition.updatedAt()).isEqualTo(updatedAt);
      assertThat(definition.version()).isEqualTo(version);
    }

    @Test
    @DisplayName("reconstitute 不注册领域事件")
    void reconstitute_doesNotRegisterEvents() {
      BusinessDefinition definition = createReconstitutedDefinition();

      assertThat(definition.getDomainEvents()).isEmpty();
    }

    @Test
    @DisplayName("reconstitute 拒绝空 businessCode")
    void reconstitute_rejectsNullBusinessCode() {
      assertThatThrownBy(() -> BusinessDefinition.reconstitute(
          DEFINITION_ID, null, "年金计划设立", "描述",
          Set.of(HANDLE_ACTION), true,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reconstitute 拒绝空 businessName")
    void reconstitute_rejectsBlankBusinessName() {
      assertThatThrownBy(() -> BusinessDefinition.reconstitute(
          DEFINITION_ID, BUSINESS_CODE, "  ", "描述",
          Set.of(HANDLE_ACTION), true,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("reconstitute 拒绝空 supportedActions 集合")
    void reconstitute_rejectsEmptyActions() {
      assertThatThrownBy(() -> BusinessDefinition.reconstitute(
          DEFINITION_ID, BUSINESS_CODE, "年金计划设立", "描述",
          Set.of(), true,
          OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
      )).isInstanceOf(IllegalStateException.class);
    }
  }

  @Nested
  @DisplayName("字段不可变性")
  class ImmutabilityTest {

    @Test
    @DisplayName("supportedActions 返回不可变集合")
    void supportedActions_returnsImmutableSet() {
      BusinessDefinition definition = createDefaultDefinition();

      Set<BusinessAction> actions = definition.supportedActions();

      assertThatThrownBy(() -> actions.add(QUERY_ACTION))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("supportedActions 内部状态不受外部集合修改影响")
    void supportedActions_isolatedFromExternalMutations() {
      Set<BusinessAction> source = new HashSet<>();
      source.add(HANDLE_ACTION);
      BusinessDefinition definition = BusinessDefinition.create(
          DEFINITION_ID, BUSINESS_CODE, "年金计划设立", "描述",
          source, OPERATOR
      );
      source.add(QUERY_ACTION);

      assertThat(definition.supportedActions()).containsExactly(HANDLE_ACTION);
    }

    @Test
    @DisplayName("getDomainEvents 返回不可变列表")
    void getDomainEvents_returnsImmutableList() {
      BusinessDefinition definition = createDefaultDefinition();

      assertThatThrownBy(() -> definition.getDomainEvents().clear())
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("businessCode 字段不可变")
    void businessCode_isImmutable() {
      BusinessDefinition definition = createDefaultDefinition();

      assertThat(definition.businessCode()).isEqualTo(BUSINESS_CODE);
    }
  }

  private BusinessDefinition createDefaultDefinition() {
    return BusinessDefinition.create(
        DEFINITION_ID, BUSINESS_CODE, "年金计划设立", "年金计划设立业务",
        Set.of(HANDLE_ACTION), OPERATOR
    );
  }

  private BusinessDefinition createReconstitutedDefinition() {
    return BusinessDefinition.reconstitute(
        DEFINITION_ID, BUSINESS_CODE, "年金计划设立", "年金计划设立业务",
        Set.of(HANDLE_ACTION), true,
        OPERATOR, OPERATOR, LocalDateTime.now(), LocalDateTime.now(), Version.initial()
    );
  }
}
