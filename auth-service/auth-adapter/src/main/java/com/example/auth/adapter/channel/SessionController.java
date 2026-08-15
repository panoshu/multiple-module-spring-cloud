package com.example.auth.adapter.channel;

import com.example.auth.api.SessionApi;
import com.example.auth.api.command.LoginRequest;
import com.example.auth.api.command.SelectPlanRequest;
import com.example.auth.api.dto.LoginResponse;
import com.example.auth.api.dto.SelectablePlansResponse;
import com.example.shared.annuity.AnnuityChannel;
import com.example.shared.contactinfo.Mobile;
import com.example.shared.identifier.id.CustomerNo;
import com.example.shared.identifier.id.PlanNo;
import com.example.shared.identifier.id.UserNo;
import com.example.shared.web.core.api.ApiResult;
import com.pension.permission.application.channel.LogoutCommand;
import com.pension.permission.application.channel.OpenSessionWithCredentialCommand;
import com.pension.permission.application.channel.SelectPlanCommand;
import com.pension.permission.application.channel.SessionApplicationService;
import com.pension.permission.domain.channel.spi.LoginTokenService;
import com.pension.permission.domain.channel.valueobject.AllPlans;
import com.pension.permission.domain.channel.valueobject.EnumeratedPlans;
import com.pension.permission.domain.channel.valueobject.SelectablePlanScope;
import com.pension.permission.domain.credential.valueobject.owner.CredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.CustomerCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.PlanCredentialOwner;
import com.pension.permission.domain.credential.valueobject.owner.UserCredentialOwner;
import com.pension.permission.types.SessionId;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 会话管理 Controller — 登录/登出/选计划/查可选计划.
 */
@RestController
@RequiredArgsConstructor
public class SessionController implements SessionApi {

  private static final String TOKEN_HEADER_INTERNET = "satoken-internet";
  private static final String TOKEN_HEADER_HQ = "satoken-hq";
  private static final String TOKEN_HEADER_BRANCH = "satoken-branch";

  private final SessionApplicationService sessionApplicationService;
  private final LoginTokenService loginTokenService;

  @Override
  public ApiResult<LoginResponse> login(LoginRequest request) {
    AnnuityChannel channel = AnnuityChannel.valueOf(request.channel());
    CredentialOwner owner = resolveOwner(request.ownerType(), request.ownerId());
    Mobile phone = request.phoneNumber() != null ? new Mobile(request.phoneNumber()) : null;

    OpenSessionWithCredentialCommand command = new OpenSessionWithCredentialCommand(
      owner, channel, request.proof(), phone);

    SessionId sessionId = sessionApplicationService.openSessionWithCredential(command);

    String tokenName = loginTokenService.getTokenName(channel);
    UserNo accountId = loginTokenService.verifyToken(sessionId.value())
      .orElse(UserNo.of("unknown"));

    LoginResponse response = new LoginResponse(
      sessionId.value(),
      tokenName,
      accountId.value(),
      channel.name()
    );
    return ApiResult.success(response);
  }

  @Override
  public ApiResult<Void> logout() {
    SessionId sessionId = currentSessionId();
    UserNo operator = currentAccountId();
    sessionApplicationService.logout(new LogoutCommand(sessionId, operator));
    return ApiResult.success(null);
  }

  @Override
  public ApiResult<Void> selectPlan(SelectPlanRequest request) {
    SessionId sessionId = currentSessionId();
    UserNo operator = currentAccountId();
    sessionApplicationService.selectPlan(
      new SelectPlanCommand(sessionId, PlanNo.of(request.planId()), operator));
    return ApiResult.success(null);
  }

  @Override
  public ApiResult<SelectablePlansResponse> listSelectablePlans() {
    SessionId sessionId = currentSessionId();
    SelectablePlanScope scope = sessionApplicationService.listSelectablePlans(sessionId);
    java.util.List<String> plans = switch (scope) {
      case EnumeratedPlans ep -> ep.plans().stream().map(p -> p.value()).toList();
      case AllPlans ap -> java.util.List.of("ALL");
    };
    return ApiResult.success(new SelectablePlansResponse(plans));
  }

  private CredentialOwner resolveOwner(String ownerType, String ownerId) {
    return switch (ownerType) {
      case "USER" -> new UserCredentialOwner(UserNo.of(ownerId));
      case "CUSTOMER" -> new CustomerCredentialOwner(CustomerNo.of(ownerId));
      case "PLAN" -> new PlanCredentialOwner(PlanNo.of(ownerId));
      default -> throw new IllegalArgumentException("未知的凭证持有者类型: " + ownerType);
    };
  }

  private SessionId currentSessionId() {
    HttpServletRequest request = currentRequest();
    // 尝试从渠道专属 token header 读取
    String token = null;
    for (String header : java.util.List.of(TOKEN_HEADER_INTERNET, TOKEN_HEADER_HQ, TOKEN_HEADER_BRANCH)) {
      String value = request.getHeader(header);
      if (value != null && !value.isBlank()) {
        token = value;
        break;
      }
    }
    if (token == null || token.isBlank()) {
      throw new IllegalStateException("未登录");
    }
    return new SessionId(token);
  }

  private UserNo currentAccountId() {
    // 从网关注入的 X-Account-Id header 解析（经过签名验证）
    HttpServletRequest request = currentRequest();
    String accountId = request.getHeader("X-Account-Id");
    if (accountId == null || accountId.isBlank()) {
      throw new IllegalStateException("未登录");
    }
    // X-Account-Id format: "loginId:expireEpochSecond" (when signed) or just "loginId" (dev mode)
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
