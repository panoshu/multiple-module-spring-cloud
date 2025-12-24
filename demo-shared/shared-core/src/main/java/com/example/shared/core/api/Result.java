package com.example.shared.core.api;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 统一 API 响应结果
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/16 21:15
 */
public record Result<T>(
  String code,
  String message,
  T data,
  long timestamp
) implements Serializable {

  @Serial
  private static final long serialVersionUID = 1L;

  public Result {
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(message, "message must not be null");
    if (timestamp == 0) timestamp = currentTimeMillis();
  }

  private static long currentTimeMillis() {
    return System.currentTimeMillis();
  }

  public static <T> Result<T> success() {
    return success(null);
  }

  public static <T> Result<T> success(T data) {
    return new Result<>(SystemCode.SUCCESS.getCode(), SystemCode.SUCCESS.getMessage(), data, System.currentTimeMillis());
  }

  public static <T> Result<T> failure(IResultCode resultCode, Object... args) {
    String msg = args.length == 0 ? resultCode.getMessage() : resultCode.formatMessage(args); // 利用前面新增的 formatMessage
    return new Result<>(
      resultCode.getCode(),
      msg,
      null,
      currentTimeMillis()
    );
  }

  public static <T> Result<T> failure(String code, String message) {
    return new Result<>(code, message, null, currentTimeMillis());
  }

  /**
   * 辅助方法
   */
  public static final String SUCCESS_CODE = SystemCode.SUCCESS.getCode();

  public boolean isSuccess() {
    return Objects.equals(SUCCESS_CODE, this.code);
  }

  // 可选：提供更安全的 isSuccess（避免字符串误配）
  public boolean isFailure() {
    return !isSuccess();
  }
}
