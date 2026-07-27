package com.example.iam.application.service;

import com.example.iam.api.command.BranchLoginCommand;
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
 * 网点渠道认证应用服务。
 *
 * <p>处理网点柜员通过柜员号 + 密码方式登录 IAM 系统。
 * 登录成功后,用户在 sa-token Token-Session 中建立 BRANCH 渠道会话。
 *
 * <p>网点渠道特殊处理:登录成功后,会话标记 {@code secondaryAuthStatus=PENDING},
 * 柜员需通过 {@link SecondaryAuthAppService} 完成二次授权后才能办理高风险业务。
 *
 * <p>本服务继承 {@link AbstractChannelAuthService},仅声明渠道差异(BRANCH + BRANCH_TELLER),
 * 通用登录流程由父类编排。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@Service
public class BranchAuthService extends AbstractChannelAuthService {

  /** 网点渠道用户归属类型 */
  private static final String BRANCH_TELLER_OWNER_TYPE = "BRANCH_TELLER";

  /**
   * 构造函数注入(显式调用父类构造函数)。
   */
  public BranchAuthService(UserRepository userRepository,
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
    return ChannelType.BRANCH;
  }

  @Override
  protected String ownerType() {
    return BRANCH_TELLER_OWNER_TYPE;
  }

  /**
   * 网点渠道登录。
   *
   * <p>网点渠道使用柜员号(tellerNo)作为登录名。
   * 登录成功后返回的 {@link LoginResultDTO#secondaryAuthRequired()} 为 false,
   * 因为二次授权是在办理具体业务时触发,而非登录时强制。
   *
   * @param command 网点渠道登录命令
   * @return 登录结果
   */
  @Transactional
  public LoginResultDTO login(BranchLoginCommand command) {
    return doLogin(command.tellerNo(), command.password(),
        command.loginIp(), command.userAgent());
  }

  /**
   * 网点渠道登出。
   *
   * <p>登出时同步清理可能存在的二次授权会话信息。
   *
   * @param command 登出命令
   */
  @Transactional
  public void logout(LogoutCommand command) {
    channelSessionPort.clearSecondaryAuthSession();
    doLogout();
  }
}
