package com.example.iam.adapter.controller;

import com.example.iam.api.command.CreateRouteRuleCommand;
import com.example.iam.api.command.DisableRouteRuleCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.RouteRuleDTO;
import com.example.iam.api.query.GetRouteRuleDetailQuery;
import com.example.iam.api.query.ListRouteRulesQuery;
import com.example.iam.application.service.RouteRuleAppService;
import com.example.iam.domain.authorization.errorcode.IamAuthzErrorCode;
import com.example.shared.exception.BusinessException;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import com.example.shared.web.core.dto.PageQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RouteRuleController} 单元测试。
 *
 * <p>Controller 仅做请求转发,测试重点验证委托关系与 {@link ApiResult} 包装。
 *
 * <p>采用纯单元测试方案(方案 C),避免 sa-token 自动配置导致的 Spring 上下文加载复杂度。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RouteRuleController 路由规则管理")
class RouteRuleControllerTest {

  private static final Long RULE_ID = 6501L;

  @Mock
  private RouteRuleAppService routeRuleAppService;

  @InjectMocks
  private RouteRuleController controller;

  private CreateRouteRuleCommand createCommand;

  @BeforeEach
  void setUp() {
    createCommand = new CreateRouteRuleCommand(
        "/api/order/**", "PERMISSION", "order:handle",
        "订单权限路由", 100, "operator01");
  }

  private static RouteRuleDTO buildRuleDTO() {
    return new RouteRuleDTO(
        RULE_ID, "/api/order/**", "PERMISSION", "order:handle",
        "订单权限路由", 100, true,
        LocalDateTime.now(), LocalDateTime.now(), 0L);
  }

  @Nested
  @DisplayName("create 创建路由规则")
  class Create {

    @Test
    @DisplayName("成功路径:委托 RouteRuleAppService 并以 ApiResult.success 包装返回新 ID")
    void success_delegatesAndWrapsAsApiResult() {
      IdResponseDTO response = new IdResponseDTO(RULE_ID);
      when(routeRuleAppService.create(createCommand)).thenReturn(response);

      ApiResult<IdResponseDTO> apiResult = controller.create(createCommand);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.message()).isEqualTo("success");
      assertThat(apiResult.data()).isSameAs(response);
      assertThat(apiResult.data().id()).isEqualTo(RULE_ID);
      verify(routeRuleAppService).create(createCommand);
    }

    @Test
    @DisplayName("异常路径:Service 抛 BusinessException(ROUTE_PATTERN_DUPLICATE)时透传")
    void serviceThrowsBusinessException_propagates() {
      BusinessException ex = new BusinessException(IamAuthzErrorCode.ROUTE_PATTERN_DUPLICATE)
          .withUserDetail("路由匹配模式重复");
      when(routeRuleAppService.create(createCommand)).thenThrow(ex);

      assertThatThrownBy(() -> controller.create(createCommand))
          .isSameAs(ex)
          .isInstanceOf(BusinessException.class);
      verify(routeRuleAppService).create(createCommand);
    }
  }

  @Nested
  @DisplayName("disable 禁用路由规则")
  class Disable {

    @Test
    @DisplayName("成功路径:委托 RouteRuleAppService 并返回无数据成功结果")
    void success_delegatesAndReturnsVoidSuccess() {
      DisableRouteRuleCommand command = new DisableRouteRuleCommand(
          RULE_ID, "operator01");

      ApiResult<Void> apiResult = controller.disable(command);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isNull();
      verify(routeRuleAppService).disable(command);
    }
  }

  @Nested
  @DisplayName("getDetail 查询路由规则详情")
  class GetDetail {

    @Test
    @DisplayName("成功路径:委托 RouteRuleAppService 并以 ApiResult.success 包装返回")
    void success_delegatesAndWrapsAsApiResult() {
      GetRouteRuleDetailQuery query = new GetRouteRuleDetailQuery(RULE_ID);
      RouteRuleDTO dto = buildRuleDTO();
      when(routeRuleAppService.getDetail(query)).thenReturn(dto);

      ApiResult<RouteRuleDTO> apiResult = controller.getDetail(query);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(dto);
      assertThat(apiResult.data().ruleId()).isEqualTo(RULE_ID);
      verify(routeRuleAppService).getDetail(query);
    }
  }

  @Nested
  @DisplayName("list 查询路由规则列表")
  class ListQuery {

    @Test
    @DisplayName("成功路径:委托 RouteRuleAppService 并以 ApiResult.success 包装返回分页结果")
    void success_delegatesAndWrapsAsApiResult() {
      ListRouteRulesQuery query = new ListRouteRulesQuery(
          "/api/order", "PERMISSION", Boolean.TRUE,
          PageQuery.firstPage(10));
      PageData<RouteRuleDTO> pageData = new PageData<>(
          1, 0, 1, false, List.of(buildRuleDTO()));
      when(routeRuleAppService.list(query)).thenReturn(pageData);

      ApiResult<PageData<RouteRuleDTO>> apiResult = controller.list(query);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(pageData);
      assertThat(apiResult.data().items()).hasSize(1);
      verify(routeRuleAppService).list(query);
    }
  }
}
