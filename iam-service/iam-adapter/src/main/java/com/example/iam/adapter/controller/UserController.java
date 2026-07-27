package com.example.iam.adapter.controller;

import com.example.iam.api.UserApi;
import com.example.iam.api.command.CreateUserCommand;
import com.example.iam.api.command.DisableUserCommand;
import com.example.iam.api.command.EnableUserCommand;
import com.example.iam.api.command.LockUserCommand;
import com.example.iam.api.command.UpdateUserProfileCommand;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.dto.UserDTO;
import com.example.iam.api.query.GetUserDetailQuery;
import com.example.iam.api.query.ListUsersQuery;
import com.example.iam.application.service.UserAppService;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理 Controller
 *
 * <p>实现 {@link UserApi} 接口,委托 {@link UserAppService} 完成用户的
 * 创建、状态变更、档案维护与查询编排。本类仅做请求转发,不包含业务逻辑与 DTO 转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController implements UserApi {

  private final UserAppService userAppService;

  @Override
  public ApiResult<IdResponseDTO> create(CreateUserCommand command) {
    log.info("创建用户: loginName={}, channelType={}", command.loginName(), command.channelType());
    return ApiResult.success(userAppService.create(command));
  }

  @Override
  public ApiResult<Void> disable(DisableUserCommand command) {
    log.info("禁用用户: userId={}", command.userId());
    userAppService.disable(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<Void> enable(EnableUserCommand command) {
    log.info("启用用户: userId={}", command.userId());
    userAppService.enable(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<Void> lock(LockUserCommand command) {
    log.info("锁定用户: userId={}, reason={}", command.userId(), command.reason());
    userAppService.lock(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<Void> updateProfile(UpdateUserProfileCommand command) {
    log.info("更新用户档案: userId={}", command.userId());
    userAppService.updateProfile(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<PageData<UserDTO>> list(ListUsersQuery query) {
    log.info("查询用户列表: channelType={}, loginName={}, status={}",
        query.channelType(), query.loginName(), query.status());
    return ApiResult.success(userAppService.list(query));
  }

  @Override
  public ApiResult<UserDTO> getDetail(GetUserDetailQuery query) {
    log.info("查询用户详情: userId={}", query.userId());
    return ApiResult.success(userAppService.getDetail(query));
  }
}
