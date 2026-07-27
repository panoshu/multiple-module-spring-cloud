package com.example.iam.application.service;

import com.example.iam.api.command.CreateRouteRuleCommand;
import com.example.iam.api.command.DisableRouteRuleCommand;
import com.example.iam.api.command.EnableRouteRuleCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.RouteRuleDTO;
import com.example.iam.api.query.GetRouteRuleDetailQuery;
import com.example.iam.api.query.ListRouteRulesQuery;
import com.example.iam.domain.authorization.aggregate.root.RouteRule;
import com.example.iam.domain.authorization.aggregate.valueobject.RouteCheckType;
import com.example.iam.domain.authorization.repository.RouteRuleRepository;
import com.example.iam.types.RouteRuleId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.primitives.identity.IdService;
import com.example.shared.primitives.identity.UserNo;
import com.example.shared.web.core.dto.PageData;
import com.example.shared.web.core.dto.PageQuery;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RouteRuleAppService} 单元测试。
 *
 * <p>覆盖路由规则创建、禁用、启用、详情查询与列表分页等核心流程,验证
 * 校验类型解析、ID 生成与聚合根事件发布等关键协作。
 *
 * @author iam-service
 */
@DisplayName("路由规则管理应用服务测试")
@ExtendWith(MockitoExtension.class)
class RouteRuleAppServiceTest {

  private static final Long RULE_ID_VALUE = 4001L;
  private static final String ROUTE_PATTERN = "api/order/**";
  private static final String CHECK_VALUE = "ORDER_HANDLE";
  private static final String DESCRIPTION = "订单权限校验";
  private static final int PRIORITY = 10;
  private static final String OPERATOR = "admin";

  @Mock private RouteRuleRepository routeRuleRepository;
  @Mock private EventBus eventBus;
  @Mock private IdService idService;

  @InjectMocks
  private RouteRuleAppService routeRuleAppService;

  @Nested
  @DisplayName("create 创建路由规则")
  class CreateTest {

    @Test
    @DisplayName("创建成功:解析校验类型、生成 ID 并保存")
    void should_create_rule_when_valid() {
      CreateRouteRuleCommand command = buildCommand(RouteCheckType.PERMISSION.name());
      when(idService.nextLongId(RouteRuleId.class, "IAM_ROUTE_RULE"))
          .thenReturn(RouteRuleId.of(RULE_ID_VALUE));

      IdResponseDTO response = routeRuleAppService.create(command);

      assertThat(response.id()).isEqualTo(RULE_ID_VALUE);
      verify(routeRuleRepository).save(any(RouteRule.class));
    }

    @Test
    @DisplayName("校验类型无效时抛业务异常,不生成 ID 不保存")
    void should_throw_when_check_type_invalid() {
      CreateRouteRuleCommand command = buildCommand("UNKNOWN_TYPE");

      assertThatThrownBy(() -> routeRuleAppService.create(command))
          .isInstanceOf(BusinessException.class);

      verify(idService, never()).nextLongId(any(), any());
      verify(routeRuleRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("disable 禁用路由规则")
  class DisableTest {

    @Test
    @DisplayName("禁用启用中的规则:状态转为 disabled 并保存")
    void should_disable_enabled_rule() {
      RouteRule rule = buildRule(true);
      when(routeRuleRepository.load(RouteRuleId.of(RULE_ID_VALUE)))
          .thenReturn(Optional.of(rule));
      DisableRouteRuleCommand command = new DisableRouteRuleCommand(RULE_ID_VALUE, OPERATOR);

      routeRuleAppService.disable(command);

      assertThat(rule.isEnabled()).isFalse();
      verify(routeRuleRepository).save(rule);
    }

    @Test
    @DisplayName("规则不存在时抛业务异常,不执行保存")
    void should_throw_when_rule_not_found() {
      when(routeRuleRepository.load(RouteRuleId.of(RULE_ID_VALUE)))
          .thenReturn(Optional.empty());
      DisableRouteRuleCommand command = new DisableRouteRuleCommand(RULE_ID_VALUE, OPERATOR);

      assertThatThrownBy(() -> routeRuleAppService.disable(command))
          .isInstanceOf(BusinessException.class);

      verify(routeRuleRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("enable 启用路由规则")
  class EnableTest {

    @Test
    @DisplayName("启用已禁用的规则:状态转为 enabled 并保存")
    void should_enable_disabled_rule() {
      RouteRule rule = buildRule(false);
      when(routeRuleRepository.load(RouteRuleId.of(RULE_ID_VALUE)))
          .thenReturn(Optional.of(rule));
      EnableRouteRuleCommand command = new EnableRouteRuleCommand(RULE_ID_VALUE, OPERATOR);

      routeRuleAppService.enable(command);

      assertThat(rule.isEnabled()).isTrue();
      verify(routeRuleRepository).save(rule);
    }
  }

  @Nested
  @DisplayName("getDetail 查询路由规则详情")
  class GetDetailTest {

    @Test
    @DisplayName("查询存在的规则返回对应 DTO")
    void should_return_dto_when_rule_exists() {
      RouteRule rule = buildRule(true);
      when(routeRuleRepository.load(RouteRuleId.of(RULE_ID_VALUE)))
          .thenReturn(Optional.of(rule));

      RouteRuleDTO dto = routeRuleAppService.getDetail(
          new GetRouteRuleDetailQuery(RULE_ID_VALUE));

      assertThat(dto).isNotNull();
      assertThat(dto.ruleId()).isEqualTo(RULE_ID_VALUE);
      assertThat(dto.routePattern()).isEqualTo(ROUTE_PATTERN);
      assertThat(dto.checkType()).isEqualTo(RouteCheckType.PERMISSION.name());
      assertThat(dto.checkValue()).isEqualTo(CHECK_VALUE);
      assertThat(dto.enabled()).isTrue();
    }

    @Test
    @DisplayName("规则不存在时抛业务异常")
    void should_throw_when_rule_not_found() {
      when(routeRuleRepository.load(RouteRuleId.of(RULE_ID_VALUE)))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> routeRuleAppService.getDetail(
          new GetRouteRuleDetailQuery(RULE_ID_VALUE)))
          .isInstanceOf(BusinessException.class);
    }
  }

  @Nested
  @DisplayName("list 路由规则列表分页查询")
  class ListTest {

    @Test
    @DisplayName("无过滤条件时返回全部规则分页结果")
    void should_return_all_rules_when_no_filter() {
      RouteRule rule1 = buildRule(true);
      when(routeRuleRepository.findAll()).thenReturn(List.of(rule1));
      ListRouteRulesQuery query = new ListRouteRulesQuery(
          null, null, null, PageQuery.firstPage(10));

      PageData<RouteRuleDTO> page = routeRuleAppService.list(query);

      assertThat(page.totalCount()).isEqualTo(1);
      assertThat(page.items()).hasSize(1);
      assertThat(page.items().get(0).ruleId()).isEqualTo(RULE_ID_VALUE);
    }
  }

  private CreateRouteRuleCommand buildCommand(String checkType) {
    return new CreateRouteRuleCommand(
        ROUTE_PATTERN, checkType, CHECK_VALUE, DESCRIPTION, PRIORITY, OPERATOR);
  }

  private RouteRule buildRule(boolean enabled) {
    return RouteRule.reconstitute(
        RouteRuleId.of(RULE_ID_VALUE), ROUTE_PATTERN,
        RouteCheckType.PERMISSION, CHECK_VALUE, DESCRIPTION, PRIORITY, enabled,
        UserNo.of(OPERATOR), UserNo.of(OPERATOR),
        LocalDateTime.now(), LocalDateTime.now(),
        com.example.shared.domain.aggregate.valueobject.Version.initial());
  }
}
