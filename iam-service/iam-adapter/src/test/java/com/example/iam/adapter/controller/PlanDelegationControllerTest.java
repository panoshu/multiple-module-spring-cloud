package com.example.iam.adapter.controller;

import com.example.iam.api.command.CreatePlanDelegationCommand;
import com.example.iam.api.command.CreatePlanDelegationCommand.DelegationPermissionItem;
import com.example.iam.api.command.RevokePlanDelegationCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.PlanDelegationDTO;
import com.example.iam.api.query.GetPlanDelegationDetailQuery;
import com.example.iam.api.query.ListPlanDelegationsQuery;
import com.example.iam.application.service.PlanDelegationAppService;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlanDelegationController} 单元测试。
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
@DisplayName("PlanDelegationController 计划代办管理")
class PlanDelegationControllerTest {

  private static final Long DELEGATION_ID = 5501L;

  @Mock
  private PlanDelegationAppService planDelegationAppService;

  @InjectMocks
  private PlanDelegationController controller;

  private CreatePlanDelegationCommand createCommand;

  @BeforeEach
  void setUp() {
    createCommand = new CreatePlanDelegationCommand(
        "DLG001", "PLAN-A", "PLAN-B", "ALL_OPERATORS",
        null, Set.of(new DelegationPermissionItem("ANNUITY_ESTABLISH", Set.of("HANDLE"))),
        LocalDateTime.now(), null, "operator01");
  }

  private static PlanDelegationDTO buildDelegationDTO() {
    return new PlanDelegationDTO(
        DELEGATION_ID, "DLG001", "PLAN-A", "PLAN-B",
        "ALL_OPERATORS", null,
        Set.of(new com.example.iam.api.dto.DelegationPermissionDTO("ANNUITY_ESTABLISH", "HANDLE")),
        "ACTIVE", LocalDateTime.now(), null,
        LocalDateTime.now(), LocalDateTime.now(), 0L);
  }

  @Nested
  @DisplayName("create 创建计划代办")
  class Create {

    @Test
    @DisplayName("成功路径:委托 PlanDelegationAppService 并以 ApiResult.success 包装返回新 ID")
    void success_delegatesAndWrapsAsApiResult() {
      IdResponseDTO response = new IdResponseDTO(DELEGATION_ID);
      when(planDelegationAppService.create(createCommand)).thenReturn(response);

      ApiResult<IdResponseDTO> apiResult = controller.create(createCommand);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.message()).isEqualTo("success");
      assertThat(apiResult.data()).isSameAs(response);
      assertThat(apiResult.data().id()).isEqualTo(DELEGATION_ID);
      verify(planDelegationAppService).create(createCommand);
    }

    @Test
    @DisplayName("异常路径:Service 抛 BusinessException(PLAN_DELEGATION_CODE_DUPLICATE)时透传")
    void serviceThrowsBusinessException_propagates() {
      BusinessException ex = new BusinessException(IamAuthzErrorCode.PLAN_DELEGATION_CODE_DUPLICATE)
          .withUserDetail("代办编码重复");
      when(planDelegationAppService.create(createCommand)).thenThrow(ex);

      assertThatThrownBy(() -> controller.create(createCommand))
          .isSameAs(ex)
          .isInstanceOf(BusinessException.class);
      verify(planDelegationAppService).create(createCommand);
    }
  }

  @Nested
  @DisplayName("revoke 撤销计划代办")
  class Revoke {

    @Test
    @DisplayName("成功路径:委托 PlanDelegationAppService 并返回无数据成功结果")
    void success_delegatesAndReturnsVoidSuccess() {
      RevokePlanDelegationCommand command = new RevokePlanDelegationCommand(
          DELEGATION_ID, "业务调整", "operator01");

      ApiResult<Void> apiResult = controller.revoke(command);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isNull();
      verify(planDelegationAppService).revoke(command);
    }
  }

  @Nested
  @DisplayName("getDetail 查询计划代办详情")
  class GetDetail {

    @Test
    @DisplayName("成功路径:委托 PlanDelegationAppService 并以 ApiResult.success 包装返回")
    void success_delegatesAndWrapsAsApiResult() {
      GetPlanDelegationDetailQuery query = new GetPlanDelegationDetailQuery(DELEGATION_ID);
      PlanDelegationDTO dto = buildDelegationDTO();
      when(planDelegationAppService.getDetail(query)).thenReturn(dto);

      ApiResult<PlanDelegationDTO> apiResult = controller.getDetail(query);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(dto);
      assertThat(apiResult.data().delegationId()).isEqualTo(DELEGATION_ID);
      verify(planDelegationAppService).getDetail(query);
    }
  }

  @Nested
  @DisplayName("list 查询计划代办列表")
  class ListQuery {

    @Test
    @DisplayName("成功路径:委托 PlanDelegationAppService 并以 ApiResult.success 包装返回分页结果")
    void success_delegatesAndWrapsAsApiResult() {
      ListPlanDelegationsQuery query = new ListPlanDelegationsQuery(
          "PLAN-A", "PLAN-B", "ALL_OPERATORS", "ACTIVE",
          PageQuery.firstPage(10));
      PageData<PlanDelegationDTO> pageData = new PageData<>(
          1, 0, 1, false, List.of(buildDelegationDTO()));
      when(planDelegationAppService.list(query)).thenReturn(pageData);

      ApiResult<PageData<PlanDelegationDTO>> apiResult = controller.list(query);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(pageData);
      assertThat(apiResult.data().items()).hasSize(1);
      verify(planDelegationAppService).list(query);
    }
  }
}
