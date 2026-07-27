package com.example.iam.application.service;

import com.example.iam.api.command.CreatePermissionRuleCommand;
import com.example.iam.api.command.DisablePermissionRuleCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.PermissionRuleDTO;
import com.example.iam.api.query.GetPermissionRuleDetailQuery;
import com.example.iam.application.port.PermissionCachePort;
import com.example.iam.domain.authorization.aggregate.root.BusinessDefinition;
import com.example.iam.domain.authorization.aggregate.root.PermissionRule;
import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessAction;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.aggregate.valueobject.OverrideMode;
import com.example.iam.domain.authorization.aggregate.valueobject.RuleStatus;
import com.example.iam.domain.authorization.aggregate.valueobject.SubjectType;
import com.example.iam.domain.authorization.repository.BusinessDefinitionRepository;
import com.example.iam.domain.authorization.repository.PermissionRuleRepository;
import com.example.iam.types.PermissionRuleId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.primitives.identity.IdService;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PermissionRuleAppService} 单元测试。
 *
 * <p>覆盖权限规则创建、禁用、详情查询等核心流程,验证业务定义预校验、
 * 唯一性校验与缓存失效等关键协作。
 *
 * @author iam-service
 */
@DisplayName("权限规则管理应用服务测试")
@ExtendWith(MockitoExtension.class)
class PermissionRuleAppServiceTest {

  private static final Long RULE_ID_VALUE = 1001L;
  private static final String RULE_CODE = "RULE_001";
  private static final String RULE_NAME = "客户级年金设立规则";
  private static final String BUSINESS_CODE = "ANNUITY_ESTABLISH";
  private static final String SUBJECT_ID = "CUST001";
  private static final String OPERATOR = "admin";

  @Mock private PermissionRuleRepository ruleRepository;
  @Mock private BusinessDefinitionRepository businessDefinitionRepository;
  @Mock private PermissionCachePort permissionCachePort;
  @Mock private EventBus eventBus;
  @Mock private IdService idService;

  @InjectMocks
  private PermissionRuleAppService permissionRuleAppService;

  @Nested
  @DisplayName("create 创建权限规则")
  class CreateTest {

    @Test
    @DisplayName("创建成功:校验唯一性、加载业务定义、生成 ID 并保存,失效缓存")
    void should_create_rule_when_valid() {
      CreatePermissionRuleCommand command = buildCommand();
      BusinessDefinition definition = buildBusinessDefinition();
      when(ruleRepository.existsByRuleCode(RULE_CODE)).thenReturn(false);
      when(businessDefinitionRepository.findByBusinessCode(BusinessCode.of(BUSINESS_CODE)))
          .thenReturn(Optional.of(definition));
      when(idService.nextLongId(PermissionRuleId.class, "IAM_PERMISSION_RULE"))
          .thenReturn(PermissionRuleId.of(RULE_ID_VALUE));

      IdResponseDTO response = permissionRuleAppService.create(command);

      assertThat(response.id()).isEqualTo(RULE_ID_VALUE);
      verify(ruleRepository).save(any(PermissionRule.class));
      verify(permissionCachePort).evictAll();
      verify(eventBus).publish(any());
    }

    @Test
    @DisplayName("规则编码已存在时抛业务异常,不生成 ID 不保存")
    void should_throw_when_rule_code_duplicate() {
      CreatePermissionRuleCommand command = buildCommand();
      when(ruleRepository.existsByRuleCode(RULE_CODE)).thenReturn(true);

      assertThatThrownBy(() -> permissionRuleAppService.create(command))
          .isInstanceOf(BusinessException.class);

      verify(idService, never()).nextLongId(any(), any());
      verify(ruleRepository, never()).save(any());
    }

    @Test
    @DisplayName("业务定义不存在时抛业务异常,不生成 ID 不保存")
    void should_throw_when_business_definition_not_found() {
      CreatePermissionRuleCommand command = buildCommand();
      when(ruleRepository.existsByRuleCode(RULE_CODE)).thenReturn(false);
      when(businessDefinitionRepository.findByBusinessCode(BusinessCode.of(BUSINESS_CODE)))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> permissionRuleAppService.create(command))
          .isInstanceOf(BusinessException.class);

      verify(idService, never()).nextLongId(any(), any());
      verify(ruleRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("disable 禁用权限规则")
  class DisableTest {

    @Test
    @DisplayName("禁用活动规则:状态转为 DISABLED、保存并失效缓存")
    void should_disable_active_rule() {
      PermissionRule rule = buildRule(RuleStatus.ACTIVE);
      when(ruleRepository.load(PermissionRuleId.of(RULE_ID_VALUE)))
          .thenReturn(Optional.of(rule));
      DisablePermissionRuleCommand command = new DisablePermissionRuleCommand(
          RULE_ID_VALUE, OPERATOR);

      permissionRuleAppService.disable(command);

      assertThat(rule.status()).isEqualTo(RuleStatus.DISABLED);
      verify(ruleRepository).save(rule);
      verify(permissionCachePort).evictAll();
    }

    @Test
    @DisplayName("规则不存在时抛业务异常,不执行保存")
    void should_throw_when_rule_not_found() {
      when(ruleRepository.load(PermissionRuleId.of(RULE_ID_VALUE)))
          .thenReturn(Optional.empty());
      DisablePermissionRuleCommand command = new DisablePermissionRuleCommand(
          RULE_ID_VALUE, OPERATOR);

      assertThatThrownBy(() -> permissionRuleAppService.disable(command))
          .isInstanceOf(BusinessException.class);

      verify(ruleRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("getDetail 查询权限规则详情")
  class GetDetailTest {

    @Test
    @DisplayName("查询存在的规则返回对应 DTO")
    void should_return_dto_when_rule_exists() {
      PermissionRule rule = buildRule(RuleStatus.ACTIVE);
      when(ruleRepository.load(PermissionRuleId.of(RULE_ID_VALUE)))
          .thenReturn(Optional.of(rule));

      PermissionRuleDTO dto = permissionRuleAppService.getDetail(
          new GetPermissionRuleDetailQuery(RULE_ID_VALUE));

      assertThat(dto).isNotNull();
      assertThat(dto.ruleId()).isEqualTo(RULE_ID_VALUE);
      assertThat(dto.ruleCode()).isEqualTo(RULE_CODE);
      assertThat(dto.businessCode()).isEqualTo(BUSINESS_CODE);
      assertThat(dto.allowedActions()).contains(Action.HANDLE.name());
    }
  }

  private CreatePermissionRuleCommand buildCommand() {
    return new CreatePermissionRuleCommand(
        RULE_CODE, RULE_NAME,
        SubjectType.CUSTOMER.name(), SUBJECT_ID,
        BUSINESS_CODE, Set.of(Action.HANDLE.name()),
        false, OverrideMode.ADD.name(),
        1, null, null, OPERATOR);
  }

  private BusinessDefinition buildBusinessDefinition() {
    return BusinessDefinition.create(
        com.example.iam.types.BusinessDefinitionId.of(2001L),
        BusinessCode.of(BUSINESS_CODE), "年金计划设立", "年金设立业务",
        Set.of(BusinessAction.of(Action.HANDLE, "办理"),
            BusinessAction.of(Action.QUERY, "查询")),
        UserNo.of(OPERATOR));
  }

  private PermissionRule buildRule(RuleStatus status) {
    return PermissionRule.reconstitute(
        PermissionRuleId.of(RULE_ID_VALUE), RULE_CODE, RULE_NAME,
        SubjectType.CUSTOMER, SUBJECT_ID,
        BusinessCode.of(BUSINESS_CODE), Set.of(Action.HANDLE),
        false, OverrideMode.ADD, 1,
        status,
        LocalDateTime.now(), null,
        UserNo.of(OPERATOR), UserNo.of(OPERATOR),
        LocalDateTime.now(), LocalDateTime.now(),
        com.example.shared.domain.aggregate.valueobject.Version.initial());
  }
}
