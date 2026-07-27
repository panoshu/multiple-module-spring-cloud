package com.example.iam.application.service;

import com.example.iam.api.command.HqLoginCommand;
import com.example.iam.api.command.LogoutCommand;
import com.example.iam.api.dto.LoginResultDTO;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.domain.authentication.repository.LoginLogRepository;
import com.example.iam.domain.authentication.repository.UserRepository;
import com.example.iam.domain.authentication.strategy.CredentialValidator;
import com.example.iam.application.port.ChannelSessionPort;
import com.example.iam.application.port.PasswordEncryptorPort;
import com.example.shared.domain.event.EventBus;
import com.example.shared.primitives.identity.IdService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 总部渠道认证应用服务。
 *
 * <p>处理总部员工通过工号 + 密码方式登录 IAM 系统。
 * 登录成功后,用户在 sa-token Token-Session 中建立 HQ 渠道会话。
 *
 * <p>本服务继承 {@link AbstractChannelAuthService},仅声明渠道差异(HQ + HQ_USER),
 * 通用登录流程由父类编排。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Service
public class HqAuthService extends AbstractChannelAuthService {

  /** 总部渠道用户归属类型 */
  private static final String HQ_USER_OWNER_TYPE = "HQ_USER";

  /**
   * 构造函数注入(显式调用父类构造函数)。
   */
  public HqAuthService(UserRepository userRepository,
                       CredentialRepository credentialRepository,
                       List<CredentialValidator> credentialValidators,
                       LoginLogRepository loginLogRepository,
                       ChannelSessionPort channelSessionPort,
                       EventBus eventBus,
                       IdService idService,
                       PasswordEncryptorPort passwordEncryptorPort) {
    super(userRepository, credentialRepository, credentialValidators,
        loginLogRepository, channelSessionPort, eventBus, idService, passwordEncryptorPort);
  }

  @Override
  protected ChannelType channelType() {
    return ChannelType.HQ;
  }

  @Override
  protected String ownerType() {
    return HQ_USER_OWNER_TYPE;
  }

  /**
   * 总部渠道登录。
   *
   * <p>总部渠道使用员工工号(employeeNo)作为登录名。
   *
   * @param command 总部渠道登录命令
   * @return 登录结果
   */
  @Transactional
  public LoginResultDTO login(HqLoginCommand command) {
    return doLogin(command.employeeNo(), command.password(),
        command.loginIp(), command.userAgent());
  }

  /**
   * 总部渠道登出。
   *
   * @param command 登出命令
   */
  @Transactional
  public void logout(LogoutCommand command) {
    doLogout();
  }
}
