package com.example.iam.adapter.controller;

import com.example.iam.api.command.ChangeCredentialCommand;
import com.example.iam.api.command.CreateCredentialCommand;
import com.example.iam.api.command.RevokeCredentialCommand;
import com.example.iam.api.dto.CredentialDTO;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.query.ListCredentialsQuery;
import com.example.iam.application.service.CredentialAppService;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
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
 * {@link CredentialController} 单元测试。
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
@DisplayName("CredentialController 凭据管理")
class CredentialControllerTest {

  private static final Long OWNER_ID = 1001L;
  private static final Long CREDENTIAL_ID = 7001L;

  @Mock
  private CredentialAppService credentialAppService;

  @InjectMocks
  private CredentialController controller;

  private CreateCredentialCommand createCommand;

  @BeforeEach
  void setUp() {
    createCommand = new CreateCredentialCommand(
        "INTERNET_USER", OWNER_ID, "PASSWORD",
        "encrypted-secret", "salt-001",
        null, null, "operator01");
  }

  private static CredentialDTO buildCredentialDTO() {
    return new CredentialDTO(
        CREDENTIAL_ID, "INTERNET_USER", OWNER_ID, "PASSWORD",
        "ACTIVE", null, null,
        LocalDateTime.now(), LocalDateTime.now(), 0L);
  }

  @Nested
  @DisplayName("create 创建凭据")
  class Create {

    @Test
    @DisplayName("成功路径:委托 CredentialAppService 并以 ApiResult.success 包装返回新 ID")
    void success_delegatesAndWrapsAsApiResult() {
      IdResponseDTO response = new IdResponseDTO(CREDENTIAL_ID);
      when(credentialAppService.create(createCommand)).thenReturn(response);

      ApiResult<IdResponseDTO> apiResult = controller.create(createCommand);

      assertThat(apiResult).isNotNull();
      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.message()).isEqualTo("success");
      assertThat(apiResult.data()).isSameAs(response);
      assertThat(apiResult.data().id()).isEqualTo(CREDENTIAL_ID);
      verify(credentialAppService).create(createCommand);
    }

    @Test
    @DisplayName("异常路径:Service 抛 BusinessException(CREDENTIAL_TYPE_DUPLICATE)时透传")
    void serviceThrowsBusinessException_propagates() {
      BusinessException ex = new BusinessException(IamAuthErrorCode.CREDENTIAL_TYPE_DUPLICATE)
          .withUserDetail("同类型凭据已存在");
      when(credentialAppService.create(createCommand)).thenThrow(ex);

      assertThatThrownBy(() -> controller.create(createCommand))
          .isSameAs(ex)
          .isInstanceOf(BusinessException.class);
      verify(credentialAppService).create(createCommand);
    }
  }

  @Nested
  @DisplayName("change 修改凭据")
  class Change {

    @Test
    @DisplayName("成功路径:委托 CredentialAppService 并返回无数据成功结果")
    void success_delegatesAndReturnsVoidSuccess() {
      ChangeCredentialCommand command = new ChangeCredentialCommand(
          CREDENTIAL_ID, "new-encrypted-secret", "new-salt",
          null, "operator01");

      ApiResult<Void> apiResult = controller.change(command);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isNull();
      verify(credentialAppService).change(command);
    }
  }

  @Nested
  @DisplayName("revoke 撤销凭据")
  class Revoke {

    @Test
    @DisplayName("成功路径:委托 CredentialAppService 并返回无数据成功结果")
    void success_delegatesAndReturnsVoidSuccess() {
      RevokeCredentialCommand command = new RevokeCredentialCommand(
          CREDENTIAL_ID, "operator01");

      ApiResult<Void> apiResult = controller.revoke(command);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isNull();
      verify(credentialAppService).revoke(command);
    }
  }

  @Nested
  @DisplayName("list 查询凭据列表")
  class ListQuery {

    @Test
    @DisplayName("成功路径:委托 CredentialAppService 并以 ApiResult.success 包装返回分页结果")
    void success_delegatesAndWrapsAsApiResult() {
      ListCredentialsQuery query = new ListCredentialsQuery(
          OWNER_ID, "INTERNET_USER", "PASSWORD", "ACTIVE",
          PageQuery.firstPage(10));
      PageData<CredentialDTO> pageData = new PageData<>(
          1, 0, 1, false, List.of(buildCredentialDTO()));
      when(credentialAppService.list(query)).thenReturn(pageData);

      ApiResult<PageData<CredentialDTO>> apiResult = controller.list(query);

      assertThat(apiResult.code()).isEqualTo(ApiResult.SUCCESS_CODE);
      assertThat(apiResult.data()).isSameAs(pageData);
      assertThat(apiResult.data().items()).hasSize(1);
      verify(credentialAppService).list(query);
    }
  }
}
