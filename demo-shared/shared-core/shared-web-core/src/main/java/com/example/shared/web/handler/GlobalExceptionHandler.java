package com.example.shared.web.handler;

import com.example.shared.exception.BusinessException;
import com.example.shared.exception.CommonError;
import com.example.shared.exception.SystemException;
import com.example.shared.web.core.api.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.util.DisconnectedClientHelper;

/**
 * 全局异常处理器
 * Order = Ordered.HIGHEST_PRECEDENCE + 100，确保在 Spring Security 异常处理器之后、默认之前
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/16 21:19
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

  /**
   * 1. 处理自定义的业务异常 (BaseException)
   */
  @ExceptionHandler(BusinessException.class)
  public ApiResult<Void> handleBaseException(BusinessException e, HttpServletRequest request) {
    log.warn("业务异常! 请求URI: [{}] | {}", request.getRequestURI(), e.logMessage(), e);
    return ApiResult.failure(e.code(), e.displayMessage());
  }

  /**
   * 2. 处理自定义的系统异常 (SystemException)
   */
  @ExceptionHandler(SystemException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public ApiResult<Void> handleSystemException(SystemException e, HttpServletRequest request) {
    log.error("系统异常! 请求URI: [{}] | 错误信息: {}", request.getRequestURI(), e.getMessage(), e);
    return ApiResult.failure(e.code(), e.displayMessage());
  }


  @ExceptionHandler({BindException.class, ConstraintViolationException.class})
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ApiResult<Void> handleValidationException(Exception e, HttpServletRequest request) {

    // 核心主干没有任何多余逻辑，清晰明了
    String errorMessage = extractValidationMessage(e);

    log.warn("参数校验失败 | URI: [{}] | Errors: [{}]", request.getRequestURI(), errorMessage);

    return ApiResult.failure(CommonError.BAD_REQUEST.getCode(), errorMessage);
  }

  // 4 资源未找到异常 (HTTP 404)
  // NoResourceFoundException: Spring Boot 3.2+ 默认抛出的 404 异常
  // NoHandlerFoundException: 如果配置了 spring.mvc.throw-exception-if-no-handler-found=true
  @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ApiResult<Void> handle404(NoResourceFoundException e, HttpServletRequest request) {
    log.error("资源未找到! 请求URI: [{}] | 错误信息: {}", request.getRequestURI(), e.getMessage());
    return ApiResult.failure(CommonError.NOT_FOUND.getCode(), "资源未找到");
  }

  // 5. 兜底异常处理（包含三道防线）
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResult<Void>> handleUnknownException(Exception e, HttpServletRequest request, HttpServletResponse response) {

    // [防线1] 客户端已断开
    if (isClientDisconnected(e)) {
      log.warn("Client disconnected: {} {}", request.getMethod(), request.getRequestURI());
      return null;
    }

    // [防线2] 响应已提交（无法再写JSON）
    if (response.isCommitted()) {
      log.warn("Response committed, skipping error write. Error: {}", e.getMessage());
      return null;
    }

    // [防线3] 真正的未知系统异常
    log.error("Unexpected Error [URI: {}]: ", request.getRequestURI(), e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(ApiResult.failure(CommonError.UNKNOWN_ERROR.getCode(), CommonError.UNKNOWN_ERROR.getMessage()));
  }

  private boolean isClientDisconnected(Throwable t) {
    // 直接委托给 Spring 官方实现，简洁且权威
    return DisconnectedClientHelper.isClientDisconnectedException(t);
  }

  /**
   * 根据不同的异常类型提取校验信息
   */
  private String extractValidationMessage(Exception e) {
    if (e instanceof BindException ex) {
      return ex.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + " " + error.getDefaultMessage())
        .findFirst()
        .orElse("参数校验失败");
    }

    if (e instanceof ConstraintViolationException ex) {
      return ex.getConstraintViolations().stream()
        .map(violation -> {
          String path = violation.getPropertyPath().toString();
          String field = path.substring(path.lastIndexOf('.') + 1);
          return field + " " + violation.getMessage();
        })
        .findFirst()
        .orElse("参数校验失败");
    }

    return "参数校验失败";
  }

}
