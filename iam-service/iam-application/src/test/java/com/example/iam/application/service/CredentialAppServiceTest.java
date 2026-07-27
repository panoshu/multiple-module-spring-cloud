package com.example.iam.application.service;

import com.example.iam.api.command.CreateCredentialCommand;
import com.example.iam.api.command.RevokeCredentialCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.application.port.PasswordEncryptorPort;
import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialStatus;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.types.CredentialId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.primitives.identity.IdService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CredentialAppService} 单元测试。
 *
 * <p>覆盖凭据创建、撤销流程,验证密码类型凭据加密、状态机校验等关键协作。
 *
 * @author iam-service
 */
@DisplayName("凭据管理应用服务测试")
@ExtendWith(MockitoExtension.class)
class CredentialAppServiceTest {

  private static final Long CREDENTIAL_ID_VALUE = 6001L;
  private static final Long OWNER_ID = 5001L;
  private static final String OWNER_TYPE = "INTERNET_USER";
  private static final String RAW_SECRET = "plain-secret";
  private static final String ENCRYPTED_SECRET = "encrypted-secret";
  private static final String OPERATOR = "admin";

  @Mock private CredentialRepository credentialRepository;
  @Mock private PasswordEncryptorPort passwordEncryptorPort;
  @Mock private EventBus eventBus;
  @Mock private IdService idService;

  @InjectMocks
  private CredentialAppService credentialAppService;

  @Nested
  @DisplayName("create 创建凭据")
  class CreateTest {

    @Test
    @DisplayName("创建密码类型凭据:加密密文、生成 ID、保存并返回新建 ID")
    void should_create_password_credential_with_encryption() {
      CreateCredentialCommand command = new CreateCredentialCommand(
          OWNER_TYPE, OWNER_ID, "PASSWORD", RAW_SECRET,
          null, null, null, OPERATOR);
      when(passwordEncryptorPort.encrypt(RAW_SECRET))
          .thenReturn(ENCRYPTED_SECRET);
      when(idService.nextLongId(CredentialId.class, "IAM_CREDENTIAL"))
          .thenReturn(CredentialId.of(CREDENTIAL_ID_VALUE));

      IdResponseDTO response = credentialAppService.create(command);

      assertThat(response.id()).isEqualTo(CREDENTIAL_ID_VALUE);
      verify(passwordEncryptorPort).encrypt(RAW_SECRET);
      verify(credentialRepository).save(any(Credential.class));
    }

    @Test
    @DisplayName("凭据类型无效时抛业务异常,不加密不保存")
    void should_throw_when_credential_type_invalid() {
      CreateCredentialCommand command = new CreateCredentialCommand(
          OWNER_TYPE, OWNER_ID, "UNKNOWN_TYPE", RAW_SECRET,
          null, null, null, OPERATOR);

      assertThatThrownBy(() -> credentialAppService.create(command))
          .isInstanceOf(BusinessException.class);

      verify(passwordEncryptorPort, never()).encrypt(any());
      verify(credentialRepository, never()).save(any(Credential.class));
    }
  }

  @Nested
  @DisplayName("revoke 撤销凭据")
  class RevokeTest {

    @Test
    @DisplayName("撤销活动凭据:状态转为 REVOKED 并保存")
    void should_revoke_active_credential() {
      Credential credential = buildCredential(CredentialStatus.ACTIVE);
      when(credentialRepository.load(CredentialId.of(CREDENTIAL_ID_VALUE)))
          .thenReturn(Optional.of(credential));
      RevokeCredentialCommand command = new RevokeCredentialCommand(CREDENTIAL_ID_VALUE, OPERATOR);

      credentialAppService.revoke(command);

      verify(credentialRepository).save(credential);
      assertThat(credential.status()).isEqualTo(CredentialStatus.REVOKED);
    }

    @Test
    @DisplayName("凭据不存在时抛业务异常,不执行保存")
    void should_throw_when_credential_not_found() {
      when(credentialRepository.load(CredentialId.of(CREDENTIAL_ID_VALUE)))
          .thenReturn(Optional.empty());
      RevokeCredentialCommand command = new RevokeCredentialCommand(CREDENTIAL_ID_VALUE, OPERATOR);

      assertThatThrownBy(() -> credentialAppService.revoke(command))
          .isInstanceOf(BusinessException.class);

      verify(credentialRepository, never()).save(any(Credential.class));
    }
  }

  private Credential buildCredential(CredentialStatus status) {
    return Credential.reconstitute(
        CredentialId.of(CREDENTIAL_ID_VALUE),
        OWNER_TYPE, OWNER_ID, CredentialType.PASSWORD,
        ENCRYPTED_SECRET, null, java.util.Map.of(),
        status, null,
        com.example.shared.primitives.identity.UserNo.of(OPERATOR),
        com.example.shared.primitives.identity.UserNo.of(OPERATOR),
        java.time.LocalDateTime.now(), java.time.LocalDateTime.now(),
        com.example.shared.domain.aggregate.valueobject.Version.initial());
  }
}
