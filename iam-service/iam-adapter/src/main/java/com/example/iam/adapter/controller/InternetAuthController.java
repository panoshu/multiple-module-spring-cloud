package com.example.iam.adapter.controller;

import com.example.iam.api.InternetAuthApi;
import com.example.iam.api.command.ConfirmSecondaryAuthCommand;
import com.example.iam.api.command.InternetLoginCommand;
import com.example.iam.api.command.LogoutCommand;
import com.example.iam.api.command.RejectSecondaryAuthCommand;
import com.example.iam.api.command.RevokeSecondaryAuthCommand;
import com.example.iam.api.dto.LoginResultDTO;
import com.example.iam.api.dto.SecondaryAuthSessionDTO;
import com.example.iam.application.service.InternetAuthService;
import com.example.iam.application.service.SecondaryAuthAppService;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网上渠道认证 Controller
 *
 * <p>实现 {@link InternetAuthApi} 接口,委托 {@link InternetAuthService} 处理网上渠道
 * 登录/登出,委托 {@link SecondaryAuthAppService} 处理二次授权确认/拒绝/撤销。
 * 本类仅做请求转发,不包含业务逻辑与 DTO 转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class InternetAuthController implements InternetAuthApi {

  private final InternetAuthService internetAuthService;
  private final SecondaryAuthAppService secondaryAuthAppService;

  @Override
  public ApiResult<LoginResultDTO> login(InternetLoginCommand command) {
    log.info("网上渠道登录: loginName={}", command.loginName());
    return ApiResult.success(internetAuthService.login(command));
  }

  @Override
  public ApiResult<Void> logout(LogoutCommand command) {
    log.info("网上渠道登出");
    internetAuthService.logout(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<SecondaryAuthSessionDTO> confirmSecondaryAuth(ConfirmSecondaryAuthCommand command) {
    log.info("确认二次授权: sessionId={}", command.sessionId());
    return ApiResult.success(secondaryAuthAppService.confirm(command));
  }

  @Override
  public ApiResult<Void> rejectSecondaryAuth(RejectSecondaryAuthCommand command) {
    log.info("拒绝二次授权: sessionId={}, reason={}", command.sessionId(), command.reason());
    secondaryAuthAppService.reject(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<Void> revokeSecondaryAuth(RevokeSecondaryAuthCommand command) {
    log.info("撤销二次授权: sessionId={}, reason={}", command.sessionId(), command.reason());
    secondaryAuthAppService.revoke(command);
    return ApiResult.success();
  }
}
