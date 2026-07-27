package com.example.iam.adapter.controller;

import com.example.iam.api.command.CreateBusinessDefinitionCommand;
import com.example.iam.api.command.CreateBusinessDefinitionCommand.BusinessActionItem;
import com.example.iam.api.command.DisableBusinessDefinitionCommand;
import com.example.iam.api.dto.BusinessDefinitionDTO;
import com.example.iam.api.dto.BusinessActionDTO;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.query.ListBusinessDefinitionsQuery;
import com.example.iam.application.service.BusinessDefinitionAppService;
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
 * {@link BusinessDefinitionController} 单元测试。
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
@DisplayName("BusinessDefinitionController 业务定义管理")
class BusinessDefinitionControllerTest {

  private static final Long DEFINITION_ID = 4001L;

  @Mock
  private BusinessDefinitionAppService businessDefinitionAppService;

  @InjectMocks
  private BusinessDefinitionController controller;

  private CreateBusinessDefinitionCommand createCommand;

  @BeforeEach
  void setUp() {
    createCommand = new CreateBusinessDefinitionCommand(
        "ANNUITY_ESTABLISH", "年金计划设立", "年金业务",
        Set.of(new BusinessActionItem("HANDLE", "办理"),
            new BusinessActionItem("QUERY", "查询")),
        "operator01");
  }

  private static BusinessDefinitionDTO buildDefinitionDTO() {
    return new BusinessDefinitionDTO(
        DEFINITION_ID, "ANNUITY_ESTABLISH", "年金计划设立", "年金业务",
        Set.of(new BusinessActionDTO("HANDLE", "办理"),
            new BusinessActionDTO("QUERY", "查询")),
        true,
        LocalDateTime.now(), LocalDateTime.now(), 0L);
  }

  @Nested
  @DisplayName("create 创建业务定义")
  class Create {

    @Test
    @DisplayName("成功路径:委托 BusinessDefinitionAppService 并以 ApiResult.success 包装返回新 ID")
    void success_delegatesAndWrapsAsApiResult() {
      IdResponseDTO response = new IdResponseDTO(DEFINITION_ID);
      when(businessDefinitionAppService.create(createCommand)).thenReturn(response);

      ApiResult<IdResponseDTO> apiResult = controller.create(createCommand);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.message()).isEqualTo("success");
      assertThat(apiResult.data()).isSameAs(response);
      assertThat(apiResult.data().id()).isEqualTo(DEFINITION_ID);
      verify(businessDefinitionAppService).create(createCommand);
    }

    @Test
    @DisplayName("异常路径:Service 抛 BusinessException(BUSINESS_CODE_DUPLICATE)时透传")
    void serviceThrowsBusinessException_propagates() {
      BusinessException ex = new BusinessException(IamAuthzErrorCode.BUSINESS_CODE_DUPLICATE)
          .withUserDetail("业务编码重复");
      when(businessDefinitionAppService.create(createCommand)).thenThrow(ex);

      assertThatThrownBy(() -> controller.create(createCommand))
          .isSameAs(ex)
          .isInstanceOf(BusinessException.class);
      verify(businessDefinitionAppService).create(createCommand);
    }
  }

  @Nested
  @DisplayName("disable 禁用业务定义")
  class Disable {

    @Test
    @DisplayName("成功路径:委托 BusinessDefinitionAppService 并返回无数据成功结果")
    void success_delegatesAndReturnsVoidSuccess() {
      DisableBusinessDefinitionCommand command = new DisableBusinessDefinitionCommand(
          DEFINITION_ID, "operator01");

      ApiResult<Void> apiResult = controller.disable(command);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isNull();
      verify(businessDefinitionAppService).disable(command);
    }
  }

  @Nested
  @DisplayName("list 查询业务定义列表")
  class ListQuery {

    @Test
    @DisplayName("成功路径:委托 BusinessDefinitionAppService 并以 ApiResult.success 包装返回分页结果")
    void success_delegatesAndWrapsAsApiResult() {
      ListBusinessDefinitionsQuery query = new ListBusinessDefinitionsQuery(
          "ANNUITY", "年金", Boolean.TRUE, PageQuery.firstPage(10));
      PageData<BusinessDefinitionDTO> pageData = new PageData<>(
          1, 0, 1, false, List.of(buildDefinitionDTO()));
      when(businessDefinitionAppService.list(query)).thenReturn(pageData);

      ApiResult<PageData<BusinessDefinitionDTO>> apiResult = controller.list(query);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(pageData);
      assertThat(apiResult.data().items()).hasSize(1);
      verify(businessDefinitionAppService).list(query);
    }
  }
}
