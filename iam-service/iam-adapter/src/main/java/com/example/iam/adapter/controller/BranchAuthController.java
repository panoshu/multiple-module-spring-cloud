package com.example.iam.adapter.controller;

import com.example.iam.api.BranchAuthApi;
import com.example.iam.api.command.BranchLoginCommand;
import com.example.iam.api.command.InitiateSecondaryAuthCommand;
import com.example.iam.api.command.LogoutCommand;
import com.example.iam.api.dto.LoginResultDTO;
import com.example.iam.api.dto.SecondaryAuthSessionDTO;
import com.example.iam.api.query.GetSecondaryAuthStatusQuery;
import com.example.iam.application.service.BranchAuthService;
import com.example.iam.application.service.SecondaryAuthAppService;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * 网点渠道认证 Controller
 *
 * <p>实现 {@link BranchAuthApi} 接口,委托 {@link BranchAuthService} 处理网点渠道登录与登出,
 * 委托 {@link SecondaryAuthAppService} 处理二次授权发起与状态查询。
 * 本类仅做请求转发,不包含业务逻辑与 DTO 转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BranchAuthController implements BranchAuthApi {

  private final BranchAuthService branchAuthService;
  private final SecondaryAuthAppService secondaryAuthAppService;

  @Override
  public ApiResult<LoginResultDTO> login(BranchLoginCommand command) {
    log.info("网点渠道登录: tellerNo={}", command.tellerNo());
    return ApiResult.success(branchAuthService.login(command));
  }

  @Override
  public ApiResult<Void> logout(LogoutCommand command) {
    log.info("网点渠道登出");
    branchAuthService.logout(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<SecondaryAuthSessionDTO> initiateSecondaryAuth(InitiateSecondaryAuthCommand command) {
    log.info("发起二次授权: approverLoginName={}, customerNo={}, planId={}",
        command.approverLoginName(), command.customerNo(), command.planId());
    return ApiResult.success(secondaryAuthAppService.initiate(command));
  }

  @Override
  public ApiResult<SecondaryAuthSessionDTO> getSecondaryAuthStatus(GetSecondaryAuthStatusQuery query) {
    log.info("查询二次授权状态: sessionId={}", query.sessionId());
    return ApiResult.success(secondaryAuthAppService.getStatus(query));
  }
}
