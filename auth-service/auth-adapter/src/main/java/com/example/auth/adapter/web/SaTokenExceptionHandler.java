package com.example.auth.adapter.web;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.example.shared.exception.CommonError;
import com.example.shared.web.core.api.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * sa-token 框架异常适配（仅 auth-service 需要）。
 * <p>
 * S-04：sa-token 是 auth-service 的实现细节，其异常到统一响应格式的映射不应由
 * {@code shared-web-core} 承担——否则所有引入 shared-web 的服务都会被迫携带
 * sa-token-core。本处理器随 auth-adapter 组件扫描注册，其他服务不再感知 sa-token。
 * <p>
 * 框架层抛出的认证/授权失败统一返回 HTTP 200 + 业务码；业务代码自身的登录态校验
 * 仍抛 {@code BusinessException(CommonError.UNAUTHORIZED)}，由全局异常处理器兜底。
 *
 * @author auth-service
 */
@Slf4j
@RestControllerAdvice
public class SaTokenExceptionHandler {

  @ExceptionHandler(NotLoginException.class)
  public ApiResult<Void> handleNotLogin(NotLoginException e, HttpServletRequest request) {
    log.warn("未登录访问! 请求URI: [{}]", request.getRequestURI());
    return ApiResult.failure(CommonError.UNAUTHORIZED.getCode(), CommonError.UNAUTHORIZED.getMessage());
  }

  @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
  public ApiResult<Void> handleNotPermission(Exception e, HttpServletRequest request) {
    log.warn("权限不足! 请求URI: [{}]", request.getRequestURI());
    return ApiResult.failure(CommonError.FORBIDDEN.getCode(), CommonError.FORBIDDEN.getMessage());
  }
}
