package com.example.shared.core.exception;

import com.example.shared.core.api.IResultCode;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.Serial;
import java.util.Objects;

/**
 * 基础异常，包含错误码
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/16 21:17
 */
@Getter
public abstract class BaseException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  private static final Object[] EMPTY_ARGS = new Object[0];

  private final String code;
  private final Object[] args;

  private BaseException(String code, @Nullable String message,
                        @Nullable Throwable cause, @Nullable Object... args) {
    super(message, cause);
    this.code = code;
    this.args = args != null ? args : EMPTY_ARGS;
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
