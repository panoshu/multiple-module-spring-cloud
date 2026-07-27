package com.example.iam.adapter.controller;

import com.example.iam.api.command.CreatePermissionRuleCommand;
import com.example.iam.api.command.DisablePermissionRuleCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.PermissionRuleDTO;
import com.example.iam.api.query.GetPermissionRuleDetailQuery;
import com.example.iam.api.query.ListPermissionRulesQuery;
import com.example.iam.application.service.PermissionRuleAppService;
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
 * {@link PermissionRuleController} 单元测试。
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
@DisplayName("PermissionRuleController 权限规则管理")
class PermissionRuleControllerTest {

  private static final Long RULE_ID = 6001L;

  @Mock
  private PermissionRuleAppService permissionRuleAppService;

  @InjectMocks
  private PermissionRuleController controller;

  private CreatePermissionRuleCommand createCommand;

  @BeforeEach
  void setUp() {
    createCommand = new CreatePermissionRuleCommand(
        "RULE001", "规则001", "CUSTOMER", "C001",
        "ANNUITY_ESTABLISH", Set.of("HANDLE", "QUERY"),
        true, "ADD", 10,
        LocalDateTime.now(), null, "operator01");
  }

  private static PermissionRuleDTO buildRuleDTO() {
    return new PermissionRuleDTO(
        RULE_ID, "RULE001", "规则001",
        "CUSTOMER", "C001", "ANNUITY_ESTABLISH",
        Set.of("HANDLE", "QUERY"), true, "ADD", 10,
        "ACTIVE", LocalDateTime.now(), null,
        LocalDateTime.now(), LocalDateTime.now(), 0L);
  }

  @Nested
  @DisplayName("create 创建权限规则")
  class Create {

    @Test
    @DisplayName("成功路径:委托 PermissionRuleAppService 并以 ApiResult.success 包装返回新 ID")
    void success_delegatesAndWrapsAsApiResult() {
      IdResponseDTO response = new IdResponseDTO(RULE_ID);
      when(permissionRuleAppService.create(createCommand)).thenReturn(response);

      ApiResult<IdResponseDTO> apiResult = controller.create(createCommand);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.message()).isEqualTo("success");
      assertThat(apiResult.data()).isSameAs(response);
      assertThat(apiResult.data().id()).isEqualTo(RULE_ID);
      verify(permissionRuleAppService).create(createCommand);
    }

    @Test
    @DisplayName("异常路径:Service 抛 BusinessException(PERMISSION_RULE_CODE_DUPLICATE)时透传")
    void serviceThrowsBusinessException_propagates() {
      BusinessException ex = new BusinessException(IamAuthzErrorCode.PERMISSION_RULE_CODE_DUPLICATE)
          .withUserDetail("规则编码重复");
      when(permissionRuleAppService.create(createCommand)).thenThrow(ex);

      assertThatThrownBy(() -> controller.create(createCommand))
          .isSameAs(ex)
          .isInstanceOf(BusinessException.class);
      verify(permissionRuleAppService).create(createCommand);
    }
  }

  @Nested
  @DisplayName("disable 禁用权限规则")
  class Disable {

    @Test
    @DisplayName("成功路径:委托 PermissionRuleAppService 并返回无数据成功结果")
    void success_delegatesAndReturnsVoidSuccess() {
      DisablePermissionRuleCommand command = new DisablePermissionRuleCommand(
          RULE_ID, "operator01");

      ApiResult<Void> apiResult = controller.disable(command);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isNull();
      verify(permissionRuleAppService).disable(command);
    }
  }

  @Nested
  @DisplayName("getDetail 查询权限规则详情")
  class GetDetail {

    @Test
    @DisplayName("成功路径:委托 PermissionRuleAppService 并以 ApiResult.success 包装返回")
    void success_delegatesAndWrapsAsApiResult() {
      GetPermissionRuleDetailQuery query = new GetPermissionRuleDetailQuery(RULE_ID);
      PermissionRuleDTO dto = buildRuleDTO();
      when(permissionRuleAppService.getDetail(query)).thenReturn(dto);

      ApiResult<PermissionRuleDTO> apiResult = controller.getDetail(query);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(dto);
      assertThat(apiResult.data().ruleId()).isEqualTo(RULE_ID);
      verify(permissionRuleAppService).getDetail(query);
    }
  }

  @Nested
  @DisplayName("list 查询权限规则列表")
  class ListQuery {

    @Test
    @DisplayName("成功路径:委托 PermissionRuleAppService 并以 ApiResult.success 包装返回分页结果")
    void success_delegatesAndWrapsAsApiResult() {
      ListPermissionRulesQuery query = new ListPermissionRulesQuery(
          "RULE001", "CUSTOMER", "C001", "ANNUITY_ESTABLISH",
          "ACTIVE", PageQuery.firstPage(10));
      PageData<PermissionRuleDTO> pageData = new PageData<>(
          1, 0, 1, false, List.of(buildRuleDTO()));
      when(permissionRuleAppService.list(query)).thenReturn(pageData);

      ApiResult<PageData<PermissionRuleDTO>> apiResult = controller.list(query);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(pageData);
      assertThat(apiResult.data().items()).hasSize(1);
      verify(permissionRuleAppService).list(query);
    }
  }
}
