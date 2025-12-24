package com.example.shared.core.handler;

import com.example.shared.core.api.BusinessCode;
import com.example.shared.core.api.Result;
import com.example.shared.core.api.SystemCode;
import com.example.shared.core.exception.BusinessException;
import com.example.shared.core.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * Order = Ordered.HIGHEST_PRECEDENCE + 100，确保在 Spring Security 异常处理器之后、默认之前
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/16 21:19
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class GlobalExceptionHandler {

  // ——————— 1. 业务异常：预期错误，返回 200 + 业务 code/msg ———————
  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<Result<Void>> handleBusinessException(
    BusinessException ex, WebRequest request) {

    log.info("Business exception occurred: code={}, args={}",
      ex.getCode(),
      Arrays.toString(ex.getArgs()));

    String message = formatMessage(ex.getCode(), ex.getMessage(), ex.getArgs());
    return ResponseEntity.ok(
      Result.failure(ex.getCode(), message)
    );
  }

  // ——————— 2. 系统异常：非预期错误，返回 500 + 通用提示，记录 ERROR ———————
  @ExceptionHandler(SystemException.class)
  public ResponseEntity<Result<Void>> handleSystemException(
    SystemException ex, WebRequest request) {

    String traceId = getTraceId(request);
    log.error("System exception [traceId={}]: code={}, message={}",
      traceId, ex.getCode(), ex.getMessage(), ex);

    // 对外返回：通用提示（避免泄露敏感信息）
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
      Result.failure(SystemCode.SYS_INTERNAL_ERROR)
    );
  }

  // ——————— 3. Validation 异常（Spring Boot 常见） ———————
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Result<Void>> handleValidationException(
    MethodArgumentNotValidException ex, WebRequest request) {

    List<FieldError> errors = ex.getBindingResult().getFieldErrors();
    String validationMsg = errors.isEmpty()
      ? "参数校验失败"
      : errors.stream()
      .map(error -> error.getField() + ": " + error.getDefaultMessage())
      .collect(Collectors.joining("; "));

    String traceId = getTraceId(request);
    String errorMsg = BusinessCode.VALIDATION_ERROR.formatMessage(validationMsg);
    log.info("[traceId={}], {}", traceId, errorMsg);

    return ResponseEntity.badRequest().body(
      Result.failure(BusinessCode.VALIDATION_ERROR.getCode(), errorMsg)
    );
  }

  // ——————— 4. 其他未捕获异常（兜底） ———————
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Result<Void>> handleUnexpectedException(
    Exception ex, WebRequest request) {

    String traceId = getTraceId(request);
    log.error("Unexpected exception [traceId={}]", traceId, ex);

    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
      Result.failure(SystemCode.SYS_UNKNOWN_ERROR)
    );
  }

  /**
   * 自定义扩展点
   * 子类可覆盖此方法，处理特定异常（如 FeignException、自定义认证异常等）
   */
  protected ResponseEntity<Result<Void>> handleCustomException(
    Exception ex, WebRequest request) {
    return null;
  }

  // ——————— 工具方法 ———————

  /**
   * 格式化消息：支持 {} 占位符（与 IResultCode.formatMessage 保持一致）
   */
  private String formatMessage(String code, String rawMessage, Object[] args) {
    if (rawMessage == null || args == null || args.length == 0) {
      return rawMessage != null ? rawMessage : "Unknown error";
    }
    try {
      return MessageFormat.format(rawMessage, args);
    } catch (Exception e) {
      log.warn("Failed to format message for code: {}, raw: {}, args: {}",
        code, rawMessage, Arrays.toString(args), e);
      return rawMessage;
    }
  }

  /**
   * 从请求中提取 traceId（适配常见链路追踪方案）
   */
  private String getTraceId(WebRequest request) {
    // 1. 优先从 MDC 获取（已通过 Filter/Interceptor 注入）
    String traceId = org.slf4j.MDC.get("traceId");
    if (traceId != null) return traceId;

    // 2. 从 header 尝试提取（如 SkyWalking, Sleuth）
    String[] candidates = {"traceId", "sw8", "X-B3-TraceId", "X-Request-ID"};
    for (String header : candidates) {
      String id = request.getHeader(header);
      if (id != null && !id.trim().isEmpty()) {
        return id;
      }
    }
    return "N/A";
  }
}
