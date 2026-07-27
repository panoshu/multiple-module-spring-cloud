package com.example.iam.adapter.controller;

import com.example.iam.api.command.ClearCurrentPlanCommand;
import com.example.iam.api.command.SelectPlanCommand;
import com.example.iam.api.dto.PlanPermissionDTO;
import com.example.iam.api.dto.SelectablePlanDTO;
import com.example.iam.api.query.GetCurrentPlanQuery;
import com.example.iam.api.query.ListSelectablePlansQuery;
import com.example.iam.application.service.PlanSelectionAppService;
import com.example.iam.domain.authorization.errorcode.IamAuthzErrorCode;
import com.example.shared.exception.BusinessException;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import com.example.shared.web.core.dto.PageQuery;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlanSelectionController} 单元测试。
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
@DisplayName("PlanSelectionController 计划选择")
class PlanSelectionControllerTest {

  private static final String PLAN_ID = "PLAN001";

  @Mock
  private PlanSelectionAppService planSelectionAppService;

  @InjectMocks
  private PlanSelectionController controller;

  private static SelectablePlanDTO buildSelectablePlan() {
    return new SelectablePlanDTO(
        PLAN_ID, "计划001", "C001", "客户001",
        "OPERATION_MODE_A", false, null);
  }

  private static PlanPermissionDTO buildPlanPermission() {
    return new PlanPermissionDTO(
        PLAN_ID, "计划001", "C001",
        Set.of("business1.handle"), LocalDateTime.now(),
        null, null);
  }

  @Nested
  @DisplayName("selectPlan 选择计划")
  class SelectPlan {

    @Test
    @DisplayName("成功路径:委托 PlanSelectionAppService 并以 ApiResult.success 包装返回")
    void success_delegatesAndWrapsAsApiResult() {
      SelectPlanCommand command = new SelectPlanCommand(PLAN_ID, "C001");
      PlanPermissionDTO permission = buildPlanPermission();
      when(planSelectionAppService.selectPlan(command)).thenReturn(permission);

      ApiResult<PlanPermissionDTO> apiResult = controller.selectPlan(command);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.message()).isEqualTo("success");
      assertThat(apiResult.data()).isSameAs(permission);
      assertThat(apiResult.data().planId()).isEqualTo(PLAN_ID);
      verify(planSelectionAppService).selectPlan(command);
    }

    @Test
    @DisplayName("异常路径:Service 抛 BusinessException(PLAN_NOT_AUTHORIZED)时透传")
    void serviceThrowsBusinessException_propagates() {
      SelectPlanCommand command = new SelectPlanCommand(PLAN_ID, "C001");
      BusinessException ex = new BusinessException(IamAuthzErrorCode.PLAN_NOT_AUTHORIZED)
          .withUserDetail("计划未授权");
      when(planSelectionAppService.selectPlan(command)).thenThrow(ex);

      assertThatThrownBy(() -> controller.selectPlan(command))
          .isSameAs(ex)
          .isInstanceOf(BusinessException.class);
      verify(planSelectionAppService).selectPlan(command);
    }
  }

  @Nested
  @DisplayName("listSelectablePlans 查询可选计划列表")
  class ListSelectablePlans {

    @Test
    @DisplayName("成功路径:委托 PlanSelectionAppService 并以 ApiResult.success 包装返回分页结果")
    void success_delegatesAndWrapsAsApiResult() {
      ListSelectablePlansQuery query = new ListSelectablePlansQuery(
          "PLAN", PageQuery.firstPage(10));
      PageData<SelectablePlanDTO> pageData = new PageData<>(
          1, 0, 1, false, List.of(buildSelectablePlan()));
      when(planSelectionAppService.listSelectablePlans(query)).thenReturn(pageData);

      ApiResult<PageData<SelectablePlanDTO>> apiResult = controller.listSelectablePlans(query);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(pageData);
      assertThat(apiResult.data().items()).hasSize(1);
      verify(planSelectionAppService).listSelectablePlans(query);
    }
  }

  @Nested
  @DisplayName("getCurrentPlan 查询当前计划")
  class GetCurrentPlan {

    @Test
    @DisplayName("成功路径:委托 PlanSelectionAppService 并以 ApiResult.success 包装返回")
    void success_delegatesAndWrapsAsApiResult() {
      GetCurrentPlanQuery query = new GetCurrentPlanQuery();
      PlanPermissionDTO permission = buildPlanPermission();
      when(planSelectionAppService.getCurrentPlan(query)).thenReturn(permission);

      ApiResult<PlanPermissionDTO> apiResult = controller.getCurrentPlan(query);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(permission);
      verify(planSelectionAppService).getCurrentPlan(query);
    }
  }

  @Nested
  @DisplayName("clearCurrentPlan 清除当前计划")
  class ClearCurrentPlan {

    @Test
    @DisplayName("成功路径:委托 PlanSelectionAppService 并返回无数据成功结果")
    void success_delegatesAndReturnsVoidSuccess() {
      ClearCurrentPlanCommand command = new ClearCurrentPlanCommand();

      ApiResult<Void> apiResult = controller.clearCurrentPlan(command);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isNull();
      verify(planSelectionAppService).clearCurrentPlan(command);
    }
  }
}
