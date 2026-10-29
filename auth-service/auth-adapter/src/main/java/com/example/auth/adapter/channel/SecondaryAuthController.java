package com.example.auth.adapter.channel;

import com.example.auth.api.SecondaryAuthApi;
import com.example.auth.api.command.CloseSecondaryAuthRequest;
import com.example.auth.api.command.ConfirmSecondaryAuthRequest;
import com.example.auth.api.command.InitiateSecondaryAuthRequest;
import com.example.auth.api.command.ResendCodeRequest;
import com.example.auth.api.command.RevokeSecondaryAuthRequest;
import com.example.auth.api.dto.InitiateSecondaryAuthResponse;
import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.application.channel.SecondaryAuthAppService;
import com.pension.permission.application.channel.command.CloseSecondaryAuthCommand;
import com.pension.permission.application.channel.command.ConfirmSecondaryAuthCommand;
import com.pension.permission.application.channel.command.InitiateSecondaryAuthCommand;
import com.pension.permission.application.channel.command.ResendCodeCommand;
import com.pension.permission.application.channel.command.RevokeSecondaryAuthCommand;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.CustomerCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.PlanCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.types.SecondaryAuthSessionId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 二次授权 Controller — 网点渠道柜员身份提升.
 */
@RestController
@RequiredArgsConstructor
public class SecondaryAuthController implements SecondaryAuthApi {

  private static final String TOKEN_HEADER_INTERNET = "satoken-internet";
  private static final String TOKEN_HEADER_HQ = "satoken-hq";
  private static final String TOKEN_HEADER_BRANCH = "satoken-branch";

  private final SecondaryAuthAppService secondaryAuthAppService;

  @Override
  public ApiResult<InitiateSecondaryAuthResponse> initiate(InitiateSecondaryAuthRequest request) {
    UserNo operator = currentAccountId();
    InitiateSecondaryAuthCommand cmd = new InitiateSecondaryAuthCommand(
      UserNo.of(request.tellerAccountId()),
      resolveOwner(request.ownerType(), request.ownerId()),
      UserNo.of(request.approverAccountId()),
      new Mobile(request.approverMobile()),
      request.planId() != null ? PlanNo.of(request.planId()) : null
    );
    SecondaryAuthSessionId sessionId = secondaryAuthAppService.initiate(cmd);
    return ApiResult.success(new InitiateSecondaryAuthResponse(sessionId.value()));
  }

  @Override
  public ApiResult<Void> confirm(ConfirmSecondaryAuthRequest request) {
    UserNo operator = currentAccountId();
    ConfirmSecondaryAuthCommand cmd = new ConfirmSecondaryAuthCommand(
      new SecondaryAuthSessionId(request.sessionId()),
      request.rawCode(),
      null, // PermissionSnapshot 由应用服务内部解析，API 层不传入
      operator
    );
    secondaryAuthAppService.confirm(cmd);
    return ApiResult.success(null);
  }

  @Override
  public ApiResult<Void> resendCode(ResendCodeRequest request) {
    UserNo operator = currentAccountId();
    ResendCodeCommand cmd = new ResendCodeCommand(
      new SecondaryAuthSessionId(request.sessionId()),
      operator
    );
    secondaryAuthAppService.resendCode(cmd);
    return ApiResult.success(null);
  }

  @Override
  public ApiResult<Void> revoke(RevokeSecondaryAuthRequest request) {
    UserNo operator = currentAccountId();
    RevokeSecondaryAuthCommand cmd = new RevokeSecondaryAuthCommand(
      new SecondaryAuthSessionId(request.sessionId()),
      operator,
      request.reason()
    );
    secondaryAuthAppService.revoke(cmd);
    return ApiResult.success(null);
  }

  @Override
  public ApiResult<Void> close(CloseSecondaryAuthRequest request) {
    UserNo operator = currentAccountId();
    CloseSecondaryAuthCommand cmd = new CloseSecondaryAuthCommand(
      new SecondaryAuthSessionId(request.sessionId()),
      operator
    );
    secondaryAuthAppService.close(cmd);
    return ApiResult.success(null);
  }

  private CredentialOwner resolveOwner(String ownerType, String ownerId) {
    return switch (ownerType) {
      case "USER" -> new UserCredentialOwner(UserNo.of(ownerId));
      case "CUSTOMER" -> new CustomerCredentialOwner(CustomerNo.of(ownerId));
      case "PLAN" -> new PlanCredentialOwner(PlanNo.of(ownerId));
      default -> throw new IllegalArgumentException("未知的凭证持有者类型: " + ownerType);
    };
  }

  private UserNo currentAccountId() {
    HttpServletRequest request = currentRequest();
    String accountId = request.getHeader("X-Account-Id");
    if (accountId == null || accountId.isBlank()) {
      throw new IllegalStateException("未登录");
    }
    int idx = accountId.lastIndexOf(':');
    String loginId = idx > 0 ? accountId.substring(0, idx) : accountId;
    return UserNo.of(loginId);
  }

  private HttpServletRequest currentRequest() {
    ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
    if (attrs == null) {
      throw new IllegalStateException("无请求上下文");
    }
    return attrs.getRequest();
  }
}
