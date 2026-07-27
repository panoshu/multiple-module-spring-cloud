package com.example.iam.application.service;

import com.example.iam.api.command.CreateUserCommand;
import com.example.iam.api.command.DisableUserCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.UserDTO;
import com.example.iam.api.query.GetUserDetailQuery;
import com.example.iam.domain.authentication.aggregate.root.User;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.aggregate.valueobject.UserStatus;
import com.example.iam.domain.authentication.repository.UserRepository;
import com.example.iam.types.UserId;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link UserAppService} 单元测试。
 *
 * <p>覆盖用户创建、禁用、详情查询等核心流程,验证应用层与领域聚合根的协作。
 *
 * @author iam-service
 */
@DisplayName("用户管理应用服务测试")
@ExtendWith(MockitoExtension.class)
class UserAppServiceTest {

  private static final Long USER_ID = 5001L;
  private static final String LOGIN_NAME = "user001";
  private static final String DISPLAY_NAME = "测试用户";
  private static final String OPERATOR = "admin";
  private static final String DISABLE_REASON = "违规操作";

  @Mock private UserRepository userRepository;
  @Mock private EventBus eventBus;
  @Mock private IdService idService;

  @InjectMocks
  private UserAppService userAppService;

  @Nested
  @DisplayName("create 创建用户")
  class CreateTest {

    @Test
    @DisplayName("创建无档案用户成功:校验唯一性、生成 ID、保存并返回新建 ID")
    void should_create_user_without_profile() {
      CreateUserCommand command = new CreateUserCommand(
          "INTERNET", LOGIN_NAME, DISPLAY_NAME,
          null, null, null, null, null, null, null, OPERATOR);
      when(userRepository.existsByLoginName(LOGIN_NAME, ChannelType.INTERNET))
          .thenReturn(false);
      when(idService.nextLongId(UserId.class, "IAM_USER"))
          .thenReturn(UserId.of(USER_ID));

      IdResponseDTO response = userAppService.create(command);

      assertThat(response.id()).isEqualTo(USER_ID);
      verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("登录名在渠道内已存在时抛业务异常,不生成 ID 不保存")
    void should_throw_when_login_name_duplicate() {
      CreateUserCommand command = new CreateUserCommand(
          "INTERNET", LOGIN_NAME, DISPLAY_NAME,
          null, null, null, null, null, null, null, OPERATOR);
      when(userRepository.existsByLoginName(LOGIN_NAME, ChannelType.INTERNET))
          .thenReturn(true);

      assertThatThrownBy(() -> userAppService.create(command))
          .isInstanceOf(BusinessException.class);

      verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
    }
  }

  @Nested
  @DisplayName("disable 禁用用户")
  class DisableTest {

    @Test
    @DisplayName("禁用活跃用户成功:保存聚合根")
    void should_disable_active_user() {
      User user = buildUser(UserStatus.ACTIVE);
      when(userRepository.load(UserId.of(USER_ID)))
          .thenReturn(Optional.of(user));
      DisableUserCommand command = new DisableUserCommand(USER_ID, DISABLE_REASON, OPERATOR);

      userAppService.disable(command);

      verify(userRepository).save(user);
      assertThat(user.status()).isEqualTo(UserStatus.DISABLED);
    }

    @Test
    @DisplayName("用户不存在时抛业务异常,不执行保存")
    void should_throw_when_user_not_found() {
      when(userRepository.load(UserId.of(USER_ID)))
          .thenReturn(Optional.empty());
      DisableUserCommand command = new DisableUserCommand(USER_ID, DISABLE_REASON, OPERATOR);

      assertThatThrownBy(() -> userAppService.disable(command))
          .isInstanceOf(BusinessException.class);

      verify(userRepository, org.mockito.Mockito.never()).save(any(User.class));
    }
  }

  @Nested
  @DisplayName("getDetail 查询用户详情")
  class GetDetailTest {

    @Test
    @DisplayName("查询存在的用户返回对应 DTO")
    void should_return_dto_when_user_exists() {
      User user = buildUser(UserStatus.ACTIVE);
      when(userRepository.load(UserId.of(USER_ID)))
          .thenReturn(Optional.of(user));

      UserDTO dto = userAppService.getDetail(new GetUserDetailQuery(USER_ID));

      assertThat(dto).isNotNull();
      assertThat(dto.userId()).isEqualTo(USER_ID);
      assertThat(dto.loginName()).isEqualTo(LOGIN_NAME);
      assertThat(dto.channelType()).isEqualTo(ChannelType.INTERNET.name());
    }
  }

  private User buildUser(UserStatus status) {
    return User.reconstitute(
        UserId.of(USER_ID), ChannelType.INTERNET, LOGIN_NAME, DISPLAY_NAME,
        status, null, null, null,
        com.example.shared.primitives.identity.UserNo.of(OPERATOR),
        com.example.shared.primitives.identity.UserNo.of(OPERATOR),
        java.time.LocalDateTime.now(), java.time.LocalDateTime.now(),
        com.example.shared.domain.aggregate.valueobject.Version.initial());
  }
}
