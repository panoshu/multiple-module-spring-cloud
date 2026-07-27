package com.example.iam.adapter.controller;

import com.example.iam.api.HqAuthApi;
import com.example.iam.api.command.HqLoginCommand;
import com.example.iam.api.command.LogoutCommand;
import com.example.iam.api.dto.LoginResultDTO;
import com.example.iam.application.service.HqAuthService;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * 总部渠道认证 Controller
 *
 * <p>实现 {@link HqAuthApi} 接口,委托 {@link HqAuthService} 处理总部渠道登录与登出。
 * 本类仅做请求转发,不包含业务逻辑与 DTO 转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class HqAuthController implements HqAuthApi {

  private final HqAuthService hqAuthService;

  @Override
  public ApiResult<LoginResultDTO> login(HqLoginCommand command) {
    log.info("总部渠道登录: employeeNo={}", command.employeeNo());
    return ApiResult.success(hqAuthService.login(command));
  }

  @Override
  public ApiResult<Void> logout(LogoutCommand command) {
    log.info("总部渠道登出");
    hqAuthService.logout(command);
    return ApiResult.success();
  }
}
