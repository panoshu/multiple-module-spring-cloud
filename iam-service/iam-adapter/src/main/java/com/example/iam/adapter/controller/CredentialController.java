package com.example.iam.adapter.controller;

import com.example.iam.api.CredentialApi;
import com.example.iam.api.command.ChangeCredentialCommand;
import com.example.iam.api.command.CreateCredentialCommand;
import com.example.iam.api.command.RevokeCredentialCommand;
import com.example.iam.api.dto.CredentialDTO;
import com.example.iam.api.dto.IdResponseDTO;
import com.example.iam.api.query.ListCredentialsQuery;
import com.example.iam.application.service.CredentialAppService;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;

/**
 * 凭据管理 Controller
 *
 * <p>实现 {@link CredentialApi} 接口,委托 {@link CredentialAppService} 完成凭据的
 * 创建、修改、撤销与查询编排。本类仅做请求转发,不包含业务逻辑与 DTO 转换。
 *
 * @author iam-service
 * @since 2026/7/26
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class CredentialController implements CredentialApi {

  private final CredentialAppService credentialAppService;

  @Override
  public ApiResult<IdResponseDTO> create(CreateCredentialCommand command) {
    log.info("创建凭据: ownerType={}, ownerId={}, credentialType={}",
        command.ownerType(), command.ownerId(), command.credentialType());
    return ApiResult.success(credentialAppService.create(command));
  }

  @Override
  public ApiResult<Void> change(ChangeCredentialCommand command) {
    log.info("修改凭据: credentialId={}", command.credentialId());
    credentialAppService.change(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<Void> revoke(RevokeCredentialCommand command) {
    log.info("撤销凭据: credentialId={}", command.credentialId());
    credentialAppService.revoke(command);
    return ApiResult.success();
  }

  @Override
  public ApiResult<PageData<CredentialDTO>> list(ListCredentialsQuery query) {
    log.info("查询凭据列表: ownerId={}, ownerType={}, credentialType={}, status={}",
        query.ownerId(), query.ownerType(), query.credentialType(), query.status());
    return ApiResult.success(credentialAppService.list(query));
  }
}
