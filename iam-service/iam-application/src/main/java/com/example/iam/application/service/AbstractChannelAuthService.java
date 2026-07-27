package com.example.iam.application.service;

import com.example.iam.api.dto.LoginResultDTO;
import com.example.iam.domain.authentication.aggregate.root.Credential;
import com.example.iam.domain.authentication.aggregate.root.LoginLog;
import com.example.iam.domain.authentication.aggregate.root.User;
import com.example.iam.domain.authentication.aggregate.valueobject.ChannelType;
import com.example.iam.domain.authentication.aggregate.valueobject.CredentialType;
import com.example.iam.domain.authentication.errorcode.IamAuthErrorCode;
import com.example.iam.domain.authentication.repository.CredentialRepository;
import com.example.iam.domain.authentication.repository.LoginLogRepository;
import com.example.iam.domain.authentication.repository.UserRepository;
import com.example.iam.domain.authentication.strategy.CredentialValidator;
import com.example.iam.application.port.ChannelSessionPort;
import com.example.iam.application.port.PasswordEncryptorPort;
import com.example.iam.types.LoginFailureRecordId;
import com.example.iam.types.LoginLogId;
import com.example.shared.domain.event.EventBus;
import com.example.shared.exception.BusinessException;
import com.example.shared.primitives.identity.IdService;
import com.example.shared.primitives.identity.UserNo;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 渠道认证应用服务抽象基类。
 *
 * <p>封装三渠道(网上/总部/网点)登录流程的公共编排逻辑,子类通过 {@link #channelType()}
 * 与 {@link #ownerType()} 声明渠道差异,实现差异化入口。
 *
 * <p>登录流程:
 * <ol>
 *   <li>按 loginName + channelType 加载用户</li>
 *   <li>校验用户状态(必须为 ACTIVE)</li>
 *   <li>加载密码类型凭据</li>
 *   <li>选择匹配的 {@link CredentialValidator} 校验密码</li>
 *   <li>记录登录日志(成功/失败)</li>
 *   <li>调用 {@link ChannelSessionPort#login} 创建会话</li>
 * </ol>
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
public abstract class AbstractChannelAuthService {

  /** 失败原因代码:用户不存在 */
  protected static final String REASON_USER_NOT_FOUND = "USER_NOT_FOUND";
  /** 失败原因代码:凭据不存在 */
  protected static final String REASON_CREDENTIAL_NOT_FOUND = "CREDENTIAL_NOT_FOUND";
  /** 失败原因代码:密码错误 */
  protected static final String REASON_WRONG_PASSWORD = "WRONG_PASSWORD";
  /** 失败原因代码:账号状态异常 */
  protected static final String REASON_ACCOUNT_STATUS_INVALID = "ACCOUNT_STATUS_INVALID";

  protected final UserRepository userRepository;
  protected final CredentialRepository credentialRepository;
  protected final List<CredentialValidator> credentialValidators;
  protected final LoginLogRepository loginLogRepository;
  protected final ChannelSessionPort channelSessionPort;
  protected final EventBus eventBus;
  protected final IdService idService;
  protected final PasswordEncryptorPort passwordEncryptorPort;

  protected AbstractChannelAuthService(UserRepository userRepository,
                                       CredentialRepository credentialRepository,
                                       List<CredentialValidator> credentialValidators,
                                       LoginLogRepository loginLogRepository,
                                       ChannelSessionPort channelSessionPort,
                                       EventBus eventBus,
                                       IdService idService,
                                       PasswordEncryptorPort passwordEncryptorPort) {
    this.userRepository = userRepository;
    this.credentialRepository = credentialRepository;
    this.credentialValidators = credentialValidators;
    this.loginLogRepository = loginLogRepository;
    this.channelSessionPort = channelSessionPort;
    this.eventBus = eventBus;
    this.idService = idService;
    this.passwordEncryptorPort = passwordEncryptorPort;
  }

  /**
   * 子类声明本服务对应的渠道类型。
   */
  protected abstract ChannelType channelType();

  /**
   * 子类声明本渠道对应的用户归属类型(用于凭据查询)。
   */
  protected abstract String ownerType();

  /**
   * 执行登录流程(三渠道通用)。
   *
   * @param loginName  登录名(网上:用户名,总部:工号,网点:柜员号)
   * @param password   明文密码(由前端加密传输,本服务再哈希)
   * @param loginIp    登录 IP(可空)
   * @param userAgent  User-Agent(可空)
   * @return 登录结果 DTO
   */
  protected LoginResultDTO doLogin(String loginName, String password,
                                   String loginIp, String userAgent) {
    ChannelType channel = channelType();
    LocalDateTime loginTime = LocalDateTime.now();

    User user = userRepository.findByLoginName(loginName, channel).orElse(null);
    if (user == null) {
      recordFailure(null, loginName, channel, loginTime, loginIp, userAgent,
          REASON_USER_NOT_FOUND, "用户不存在");
      return failureResult(loginName, channel, "登录名或密码错误");
    }

    if (!user.status().isActive()) {
      recordFailure(user.id().value(), loginName, channel, loginTime, loginIp, userAgent,
          REASON_ACCOUNT_STATUS_INVALID, "账号状态不允许登录: " + user.status());
      return failureResult(loginName, channel, "账号状态不允许登录");
    }

    Credential credential = credentialRepository
        .findActive(user.id().value(), ownerType(), CredentialType.PASSWORD)
        .orElse(null);
    if (credential == null) {
      recordFailure(user.id().value(), loginName, channel, loginTime, loginIp, userAgent,
          REASON_CREDENTIAL_NOT_FOUND, "未找到密码类型凭据");
      return failureResult(loginName, channel, "登录名或密码错误");
    }

    // 通过 CredentialValidator SPI 校验密码(委托给 PasswordCredentialValidator)
    boolean passwordMatches;
    try {
      passwordMatches = credential.verify(password, selectValidator(CredentialType.PASSWORD));
    } catch (com.example.shared.exception.DomainException de) {
      // 凭据已过期/已撤销等状态异常,按密码错误处理(不暴露具体原因给前端)
      recordFailure(user.id().value(), loginName, channel, loginTime, loginIp, userAgent,
          REASON_ACCOUNT_STATUS_INVALID, "凭据状态不允许校验: " + de.getMessage());
      return failureResult(loginName, channel, "登录名或密码错误");
    }
    if (!passwordMatches) {
      recordFailure(user.id().value(), loginName, channel, loginTime, loginIp, userAgent,
          REASON_WRONG_PASSWORD, "密码校验失败");
      return failureResult(loginName, channel, "登录名或密码错误");
    }

    user.markLoginSuccess(loginIp, loginTime, UserNo.of(user.id().toString()));
    userRepository.save(user);
    publishEvents(user);

    LoginLogId logId = idService.nextLongId(LoginLogId.class, "IAM_LOGIN_LOG");
    LoginLog successLog = LoginLog.createSuccess(
        logId, user.id().value(), loginName, channel, loginTime, loginIp, userAgent,
        UserNo.of(user.id().toString()));
    loginLogRepository.save(successLog);
    publishEvents(successLog);

    channelSessionPort.login(user.id().value(), channel);

    log.info("登录成功: userId={}, loginName={}, channelType={}",
        user.id().value(), loginName, channel);
    return new LoginResultDTO(
        true, null, null,
        user.id().value(), channel.name(),
        null, false);
  }

  /**
   * 登出当前渠道会话。
   */
  protected void doLogout() {
    channelSessionPort.logout(channelType());
    log.info("登出成功: channelType={}", channelType());
  }

  /**
   * 记录登录失败日志。
   */
  private void recordFailure(Long userId, String loginName, ChannelType channel,
                             LocalDateTime loginTime, String loginIp, String userAgent,
                             String reason, String detail) {
    LoginLogId logId = idService.nextLongId(LoginLogId.class, "IAM_LOGIN_LOG");
    LoginFailureRecordId recordId = idService.nextLongId(LoginFailureRecordId.class, "IAM_LOGIN_FAIL");
    LoginLog failureLog = LoginLog.createFailure(
        logId, userId, loginName, channel, loginTime, loginIp, userAgent,
        recordId, reason, detail,
        UserNo.of(Objects.requireNonNullElse(userId, 0L).toString()));
    loginLogRepository.save(failureLog);
    publishEvents(failureLog);
    log.warn("登录失败: loginName={}, channelType={}, reason={}", loginName, channel, reason);
  }

  /**
   * 构建登录失败结果。
   */
  private LoginResultDTO failureResult(String loginName, ChannelType channel, String message) {
    return new LoginResultDTO(
        false, null, null, null, channel.name(), message, false);
  }

  /**
   * 发布聚合根的领域事件并清理。
   */
  protected void publishEvents(com.example.shared.domain.aggregate.root.AggregateRoot<?> aggregate) {
    aggregate.getDomainEvents().forEach(eventBus::publish);
    aggregate.clearDomainEvents();
  }

  /**
   * 选择匹配凭据类型的验证器。
   *
   * <p>用于非密码类型凭据(UKey/动态令牌)的校验。当前实现仅支持密码,
   * 其他类型凭据的登录流程需在子类中扩展。
   *
   * @param credentialType 凭据类型
   * @return 匹配的验证器
   * @throws BusinessException 无匹配验证器时抛出
   */
  protected CredentialValidator selectValidator(CredentialType credentialType) {
    return credentialValidators.stream()
        .filter(v -> v.supports() == credentialType)
        .findFirst()
        .orElseThrow(() -> new BusinessException(IamAuthErrorCode.CREDENTIAL_TYPE_NOT_SUPPORTED)
            .withUserDetail("未找到匹配的凭据验证器")
            .withContext("credentialType", credentialType));
  }
}
