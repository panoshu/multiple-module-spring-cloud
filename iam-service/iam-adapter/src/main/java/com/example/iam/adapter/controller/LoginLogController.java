package com.example.iam.adapter.controller;

import com.example.iam.api.LoginLogApi;
import com.example.iam.api.dto.LoginLogDTO;
import com.example.iam.api.query.ListLoginLogsQuery;
import com.example.iam.application.service.LoginLogAppService;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志查询 Controller
 *
 * <p>实现 {@link LoginLogApi} 接口,委托 {@link LoginLogAppService} 完成登录审计日志的
 * 分页查询编排。本类仅做请求转发,不包含业务逻辑与 DTO 转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class LoginLogController implements LoginLogApi {

  private final LoginLogAppService loginLogAppService;

  @Override
  public ApiResult<PageData<LoginLogDTO>> list(ListLoginLogsQuery query) {
    log.info("查询登录日志列表: userId={}, loginName={}, channelType={}, success={}",
        query.userId(), query.loginName(), query.channelType(), query.success());
    return ApiResult.success(loginLogAppService.list(query));
  }
}
