package com.example.iam.adapter.controller;

import com.example.iam.api.command.CreateUserCommand;
import com.example.iam.api.command.DisableUserCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.UserDTO;
import com.example.iam.api.query.GetUserDetailQuery;
import com.example.iam.application.service.UserAppService;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.shared.exception.BusinessException;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
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
 * {@link UserController} 单元测试。
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
@DisplayName("UserController 用户管理")
class UserControllerTest {

  private static final Long USER_ID = 1001L;
  private static final Long CREATED_ID = 5001L;

  @Mock
  private UserAppService userAppService;

  @InjectMocks
  private UserController controller;

  private CreateUserCommand createCommand;

  @BeforeEach
  void setUp() {
    createCommand = new CreateUserCommand(
        "INTERNET", "user01", "用户01",
        "user01@example.com", "13800000000",
        "Org-A", "Dev", null, null,
        null, "operator01");
  }

  private static UserDTO buildUserDTO() {
    return new UserDTO(
        USER_ID, "INTERNET", "user01", "用户01", "ACTIVE",
        LocalDateTime.now(), "10.0.0.1", null,
        LocalDateTime.now(), LocalDateTime.now(), 0L);
  }

  @Nested
  @DisplayName("create 创建用户")
  class Create {

    @Test
    @DisplayName("成功路径:委托 UserAppService 并以 ApiResult.success 包装返回新 ID")
    void success_delegatesAndWrapsAsApiResult() {
      IdResponseDTO response = new IdResponseDTO(CREATED_ID);
      when(userAppService.create(createCommand)).thenReturn(response);

      ApiResult<IdResponseDTO> apiResult = controller.create(createCommand);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.message()).isEqualTo("success");
      assertThat(apiResult.data()).isSameAs(response);
      assertThat(apiResult.data().id()).isEqualTo(CREATED_ID);
      verify(userAppService).create(createCommand);
    }

    @Test
    @DisplayName("异常路径:Service 抛 BusinessException(LOGIN_NAME_DUPLICATE)时透传")
    void serviceThrowsBusinessException_propagates() {
      BusinessException ex = new BusinessException(IamAuthErrorCode.LOGIN_NAME_DUPLICATE)
          .withUserDetail("登录名在指定渠道内已存在");
      when(userAppService.create(createCommand)).thenThrow(ex);

      assertThatThrownBy(() -> controller.create(createCommand))
          .isSameAs(ex)
          .isInstanceOf(BusinessException.class);
      verify(userAppService).create(createCommand);
    }
  }

  @Nested
  @DisplayName("disable 禁用用户")
  class Disable {

    @Test
    @DisplayName("成功路径:委托 UserAppService 并返回无数据成功结果")
    void success_delegatesAndReturnsVoidSuccess() {
      DisableUserCommand command = new DisableUserCommand(
          USER_ID, "违规操作", "operator01");

      ApiResult<Void> apiResult = controller.disable(command);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isNull();
      verify(userAppService).disable(command);
    }
  }

  @Nested
  @DisplayName("getDetail 查询用户详情")
  class GetDetail {

    @Test
    @DisplayName("成功路径:委托 UserAppService 并以 ApiResult.success 包装返回")
    void success_delegatesAndWrapsAsApiResult() {
      GetUserDetailQuery query = new GetUserDetailQuery(USER_ID);
      UserDTO dto = buildUserDTO();
      when(userAppService.getDetail(query)).thenReturn(dto);

      ApiResult<UserDTO> apiResult = controller.getDetail(query);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(dto);
      assertThat(apiResult.data().userId()).isEqualTo(USER_ID);
      verify(userAppService).getDetail(query);
    }

    @Test
    @DisplayName("异常路径:Service 抛 BusinessException(USER_NOT_FOUND)时透传")
    void serviceThrowsBusinessException_propagates() {
      GetUserDetailQuery query = new GetUserDetailQuery(USER_ID);
      BusinessException ex = new BusinessException(IamAuthErrorCode.USER_NOT_FOUND)
          .withUserDetail("用户不存在");
      when(userAppService.getDetail(query)).thenThrow(ex);

      assertThatThrownBy(() -> controller.getDetail(query))
          .isSameAs(ex)
          .isInstanceOf(BusinessException.class);
      verify(userAppService).getDetail(query);
    }
  }

  @Nested
  @DisplayName("list 查询用户列表")
  class ListQuery {

    @Test
    @DisplayName("成功路径:委托 UserAppService 并以 ApiResult.success 包装返回分页结果")
    void success_delegatesAndWrapsAsApiResult() {
      com.example.iam.api.query.ListUsersQuery query = new com.example.iam.api.query.ListUsersQuery(
          "INTERNET", "user", "ACTIVE",
          com.example.shared.web.core.dto.PageQuery.firstPage(10));
      PageData<UserDTO> pageData = new PageData<>(
          1, 0, 1, false, List.of(buildUserDTO()));
      when(userAppService.list(query)).thenReturn(pageData);

      ApiResult<PageData<UserDTO>> apiResult = controller.list(query);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(pageData);
      assertThat(apiResult.data().items()).hasSize(1);
      verify(userAppService).list(query);
    }
  }
}
