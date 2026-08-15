package com.example.auth.api;

import com.example.auth.api.command.LoginRequest;
import com.example.auth.api.command.SelectPlanRequest;
import com.example.auth.api.dto.LoginResponse;
import com.example.auth.api.dto.SelectablePlansResponse;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 会话管理 API（登录/登出/选计划/查可选计划）.
 *
 * <p>登录请求通过网关白名单路径到达 auth-service：
 * <ul>
 *   <li>/internet/auth/login — 网上渠道</li>
 *   <li>/hq/auth/login — 总部渠道</li>
 *   <li>/branch/auth/login — 网点渠道</li>
 * </ul>
 */
@HttpExchange("/auth")
public interface SessionApi {

  @PostExchange("/login")
  ApiResult<LoginResponse> login(@RequestBody @Valid LoginRequest request);

  @PostExchange("/logout")
  ApiResult<Void> logout();

  @PostExchange("/select-plan")
  ApiResult<Void> selectPlan(@RequestBody @Valid SelectPlanRequest request);

  @PostExchange("/selectable-plans")
  ApiResult<SelectablePlansResponse> listSelectablePlans();
}
