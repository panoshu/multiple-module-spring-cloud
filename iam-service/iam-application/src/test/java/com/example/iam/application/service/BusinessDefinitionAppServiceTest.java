package com.example.iam.application.service;

import com.example.iam.api.command.CreateBusinessDefinitionCommand;
import com.example.iam.api.command.CreateBusinessDefinitionCommand.BusinessActionItem;
import com.example.iam.api.command.DisableBusinessDefinitionCommand;
import com.example.iam.api.command.EnableBusinessDefinitionCommand;
import com.example.iam.api.dto.BusinessDefinitionDTO;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.query.ListBusinessDefinitionsQuery;
import com.example.iam.application.port.PermissionCachePort;
import com.example.iam.domain.authorization.aggregate.root.BusinessDefinition;
import com.example.iam.domain.authorization.aggregate.valueobject.Action;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessAction;
import com.example.iam.domain.authorization.aggregate.valueobject.BusinessCode;
import com.example.iam.domain.authorization.repository.BusinessDefinitionRepository;
import com.example.iam.types.BusinessDefinitionId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.primitives.identity.IdService;
import com.example.shared.primitives.identity.UserNo;
import com.example.shared.web.core.dto.PageData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BusinessDefinitionAppService} 单元测试。
 *
 * <p>覆盖业务定义创建、禁用、启用与列表查询等核心流程,验证业务编码唯一性校验、
 * ID 生成、缓存失效与事件发布等关键协作。
 *
 * @author iam-service
 */
@DisplayName("业务定义管理应用服务测试")
@ExtendWith(MockitoExtension.class)
class BusinessDefinitionAppServiceTest {

  private static final Long DEFINITION_ID_VALUE = 5001L;
  private static final String BUSINESS_CODE = "ANNUITY_ESTABLISH";
  private static final String BUSINESS_NAME = "年金计划设立";
  private static final String DESCRIPTION = "年金设立业务";
  private static final String OPERATOR = "admin";

  @Mock private BusinessDefinitionRepository businessDefinitionRepository;
  @Mock private PermissionCachePort permissionCachePort;
  @Mock private EventBus eventBus;
  @Mock private IdService idService;

  @InjectMocks
  private BusinessDefinitionAppService businessDefinitionAppService;

  @Nested
  @DisplayName("create 创建业务定义")
  class CreateTest {

    @Test
    @DisplayName("创建成功:校验唯一性、生成 ID、保存并失效缓存")
    void should_create_definition_when_valid() {
      CreateBusinessDefinitionCommand command = buildCommand();
      when(businessDefinitionRepository.existsByBusinessCode(BusinessCode.of(BUSINESS_CODE)))
          .thenReturn(false);
      when(idService.nextLongId(BusinessDefinitionId.class, "IAM_BIZ_DEFINITION"))
          .thenReturn(BusinessDefinitionId.of(DEFINITION_ID_VALUE));

      IdResponseDTO response = businessDefinitionAppService.create(command);

      assertThat(response.id()).isEqualTo(DEFINITION_ID_VALUE);
      verify(businessDefinitionRepository).save(any(BusinessDefinition.class));
      verify(permissionCachePort).evictAll();
    }

    @Test
    @DisplayName("业务编码已存在时抛业务异常,不生成 ID 不保存")
    void should_throw_when_business_code_duplicate() {
      CreateBusinessDefinitionCommand command = buildCommand();
      when(businessDefinitionRepository.existsByBusinessCode(BusinessCode.of(BUSINESS_CODE)))
          .thenReturn(true);

      assertThatThrownBy(() -> businessDefinitionAppService.create(command))
          .isInstanceOf(BusinessException.class);

      verify(idService, never()).nextLongId(any(), any());
      verify(businessDefinitionRepository, never()).save(any());
    }

    @Test
    @DisplayName("动作枚举无效时抛业务异常,不生成 ID 不保存")
    void should_throw_when_action_invalid() {
      CreateBusinessDefinitionCommand command = new CreateBusinessDefinitionCommand(
          BUSINESS_CODE, BUSINESS_NAME, DESCRIPTION,
          Set.of(new BusinessActionItem("UNKNOWN_ACTION", "未知动作")),
          OPERATOR);
      when(businessDefinitionRepository.existsByBusinessCode(BusinessCode.of(BUSINESS_CODE)))
          .thenReturn(false);

      assertThatThrownBy(() -> businessDefinitionAppService.create(command))
          .isInstanceOf(BusinessException.class);

      verify(idService, never()).nextLongId(any(), any());
      verify(businessDefinitionRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("disable 禁用业务定义")
  class DisableTest {

    @Test
    @DisplayName("禁用启用中的业务定义:状态转为 inactive、保存并失效缓存")
    void should_disable_active_definition() {
      BusinessDefinition definition = buildDefinition(true);
      when(businessDefinitionRepository.load(BusinessDefinitionId.of(DEFINITION_ID_VALUE)))
          .thenReturn(Optional.of(definition));
      DisableBusinessDefinitionCommand command = new DisableBusinessDefinitionCommand(
          DEFINITION_ID_VALUE, OPERATOR);

      businessDefinitionAppService.disable(command);

      assertThat(definition.isActive()).isFalse();
      verify(businessDefinitionRepository).save(definition);
      verify(permissionCachePort).evictAll();
    }

    @Test
    @DisplayName("业务定义不存在时抛业务异常,不执行保存")
    void should_throw_when_definition_not_found() {
      when(businessDefinitionRepository.load(BusinessDefinitionId.of(DEFINITION_ID_VALUE)))
          .thenReturn(Optional.empty());
      DisableBusinessDefinitionCommand command = new DisableBusinessDefinitionCommand(
          DEFINITION_ID_VALUE, OPERATOR);

      assertThatThrownBy(() -> businessDefinitionAppService.disable(command))
          .isInstanceOf(BusinessException.class);

      verify(businessDefinitionRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("enable 启用业务定义")
  class EnableTest {

    @Test
    @DisplayName("启用已禁用的业务定义:状态转为 active、保存并失效缓存")
    void should_enable_disabled_definition() {
      BusinessDefinition definition = buildDefinition(false);
      when(businessDefinitionRepository.load(BusinessDefinitionId.of(DEFINITION_ID_VALUE)))
          .thenReturn(Optional.of(definition));
      EnableBusinessDefinitionCommand command = new EnableBusinessDefinitionCommand(
          DEFINITION_ID_VALUE, OPERATOR);

      businessDefinitionAppService.enable(command);

      assertThat(definition.isActive()).isTrue();
      verify(businessDefinitionRepository).save(definition);
      verify(permissionCachePort).evictAll();
    }
  }

  @Nested
  @DisplayName("list 业务定义列表查询")
  class ListTest {

    @Test
    @DisplayName("无过滤条件且无分页参数时返回全部业务定义")
    void should_return_all_definitions_when_no_filter_and_no_paging() {
      BusinessDefinition definition = buildDefinition(true);
      when(businessDefinitionRepository.findAll())
          .thenReturn(List.of(definition));
      ListBusinessDefinitionsQuery query = new ListBusinessDefinitionsQuery(
          null, null, null, null);

      PageData<BusinessDefinitionDTO> page = businessDefinitionAppService.list(query);

      assertThat(page.totalCount()).isEqualTo(1);
      assertThat(page.items()).hasSize(1);
      assertThat(page.items().get(0).businessCode()).isEqualTo(BUSINESS_CODE);
      assertThat(page.items().get(0).businessName()).isEqualTo(BUSINESS_NAME);
      assertThat(page.items().get(0).active()).isTrue();
    }

    @Test
    @DisplayName("按业务编码精确过滤返回匹配项")
    void should_filter_by_business_code() {
      BusinessDefinition definition = buildDefinition(true);
      when(businessDefinitionRepository.findAll())
          .thenReturn(List.of(definition));
      ListBusinessDefinitionsQuery query = new ListBusinessDefinitionsQuery(
          BUSINESS_CODE, null, null, null);

      PageData<BusinessDefinitionDTO> page = businessDefinitionAppService.list(query);

      assertThat(page.totalCount()).isEqualTo(1);
      assertThat(page.items().get(0).businessCode()).isEqualTo(BUSINESS_CODE);
    }

    @Test
    @DisplayName("按业务编码过滤未匹配时返回空列表")
    void should_return_empty_when_business_code_not_match() {
      BusinessDefinition definition = buildDefinition(true);
      when(businessDefinitionRepository.findAll())
          .thenReturn(List.of(definition));
      ListBusinessDefinitionsQuery query = new ListBusinessDefinitionsQuery(
          "OTHER_BIZ", null, null, null);

      PageData<BusinessDefinitionDTO> page = businessDefinitionAppService.list(query);

      assertThat(page.totalCount()).isZero();
      assertThat(page.items()).isEmpty();
    }
  }

  private CreateBusinessDefinitionCommand buildCommand() {
    return new CreateBusinessDefinitionCommand(
        BUSINESS_CODE, BUSINESS_NAME, DESCRIPTION,
        Set.of(new BusinessActionItem(Action.HANDLE.name(), "办理"),
            new BusinessActionItem(Action.QUERY.name(), "查询")),
        OPERATOR);
  }

  private BusinessDefinition buildDefinition(boolean active) {
    return BusinessDefinition.reconstitute(
        BusinessDefinitionId.of(DEFINITION_ID_VALUE),
        BusinessCode.of(BUSINESS_CODE), BUSINESS_NAME, DESCRIPTION,
        Set.of(BusinessAction.of(Action.HANDLE, "办理"),
            BusinessAction.of(Action.QUERY, "查询")),
        active,
        UserNo.of(OPERATOR), UserNo.of(OPERATOR),
        LocalDateTime.now(), LocalDateTime.now(),
        com.example.shared.domain.aggregate.valueobject.Version.initial());
  }
}
