package com.example.shared.web.core.api;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 统一 API 响应结果
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/16 21:15
 */
public record ApiResult<T>(
  String code,
  String message,
  T data,
  Long timestamp
) implements Serializable {

  public static final String SUCCESS_CODE = "COMMON.0000";
  @Serial
  private static final long serialVersionUID = 1L;

  public ApiResult {
    Objects.requireNonNull(code, "code must not be null");
    Objects.requireNonNull(message, "message must not be null");
    timestamp = Objects.requireNonNullElse(timestamp, System.currentTimeMillis());
  }

  public static <T> ApiResult<T> success(T data) {
    return new ApiResult<>(SUCCESS_CODE, "success", data, null);
  }

  public static <T> ApiResult<T> success() {
    return success(null);
  }

  public static <T> ApiResult<T> failure(String code, String message) {
    return new ApiResult<>(code, message, null, null);
  }

  public boolean isSuccess() {
    return Objects.equals(SUCCESS_CODE, this.code);
  }

  public boolean isFailure() {
    return !isSuccess();
  }
}
