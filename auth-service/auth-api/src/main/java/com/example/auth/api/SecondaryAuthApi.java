package com.example.auth.api;

import com.example.auth.api.command.CloseSecondaryAuthRequest;
import com.example.auth.api.command.ConfirmSecondaryAuthRequest;
import com.example.auth.api.command.InitiateSecondaryAuthRequest;
import com.example.auth.api.command.ResendCodeRequest;
import com.example.auth.api.command.RevokeSecondaryAuthRequest;
import com.example.auth.api.dto.InitiateSecondaryAuthResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 二次授权 API（网点渠道柜员身份提升）.
 *
 * <p>仅限网点渠道 (BANK_BRANCH) 使用。柜员登录后需完成二次授权
 * 才能选择计划并执行业务操作。
 *
 * <p>请求路径通过网关前缀区分渠道：
 * <ul>
 *   <li>/branch/auth/secondary-auth/initiate — 发起</li>
 *   <li>/branch/auth/secondary-auth/confirm — 确认</li>
 *   <li>/branch/auth/secondary-auth/resend-code — 重发验证码</li>
 *   <li>/branch/auth/secondary-auth/revoke — 撤销</li>
 *   <li>/branch/auth/secondary-auth/close — 关闭</li>
 * </ul>
 */
@HttpExchange("/auth/secondary-auth")
public interface SecondaryAuthApi {

  @PostExchange("/initiate")
  ApiResult<InitiateSecondaryAuthResponse> initiate(@RequestBody @Valid InitiateSecondaryAuthRequest request);

  @PostExchange("/confirm")
  ApiResult<Void> confirm(@RequestBody @Valid ConfirmSecondaryAuthRequest request);

  @PostExchange("/resend-code")
  ApiResult<Void> resendCode(@RequestBody @Valid ResendCodeRequest request);

  @PostExchange("/revoke")
  ApiResult<Void> revoke(@RequestBody @Valid RevokeSecondaryAuthRequest request);

  @PostExchange("/close")
  ApiResult<Void> close(@RequestBody @Valid CloseSecondaryAuthRequest request);
}
