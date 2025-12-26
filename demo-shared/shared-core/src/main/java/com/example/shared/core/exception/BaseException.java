package com.example.shared.core.exception;

import com.example.shared.core.api.IResultCode;
import lombok.Getter;
import lombok.NonNull;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.lang.Nullable;

import java.io.Serial;
import java.util.Objects;

/**
 * 基础异常，包含错误码
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/16 21:17
 */
public abstract class BaseException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final Object[] EMPTY_ARGS = new Object[0];

  @Getter
  private final String code;

  @Getter
  private final Object[] args;

  private String detailMessage;

  private BaseException(String code, @Nullable String messageTemplate,
                        @Nullable Throwable cause, @Nullable Object... args) {
    super(format(messageTemplate, args), cause);
    this.code = code;
    this.args = args != null ? args : EMPTY_ARGS;
  }

  private static String format(String template, Object... args) {
    if (template == null) return "Unknown Error";
    if (args == null || args.length == 0) return template;
    return MessageFormatter.arrayFormat(template, args).getMessage();
  }

  protected BaseException(IResultCode resultCode) {
    this(resultCode.getCode(), resultCode.getMessage(), null, EMPTY_ARGS);
  }

  protected BaseException(IResultCode resultCode, Object... args) {
    this(resultCode.getCode(), resultCode.getMessage(), null, args);
  }

  protected BaseException(IResultCode resultCode, Throwable cause) {
    this(resultCode.getCode(), resultCode.getMessage(), cause, EMPTY_ARGS);
  }

  protected BaseException(IResultCode resultCode, Throwable cause, Object... args) {
    this(resultCode.getCode(), resultCode.getMessage(), cause, args);
  }

  /**
   * 流式 API：添加内部日志详情 (支持 {} 占位符)
   * 用法：throw new BusinessException(...).withDetail("内部接口返回: {}", response);
   */
  public BaseException withDetail(String pattern, Object... detailArgs) {
    this.detailMessage = format(pattern, detailArgs);
    return this;
  }

  public String getFormatContent() {
    return String.format(
      "exception code: %s, user message: %s, detail message: %s",
      this.code, super.getMessage(), this.detailMessage);
  }

  /**
   * 保留自定义创建入口
   */
  protected BaseException(String code, String message) {
    this(code, message, null, EMPTY_ARGS);
  }

  protected BaseException(String code, String message, Object... args) {
    this(code, message, null, args);
  }

  public static String getMessageOrDefault(@NonNull Throwable t) {
    return Objects.requireNonNullElseGet(t.getMessage(), t.getClass()::getSimpleName);
  }

}
