package com.example.shared.core.exception;

import com.example.shared.core.api.IResultCode;
import lombok.Getter;

import java.io.IOException;
import java.io.Serial;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;

/**
 * 系统异常：由基础设施/代码缺陷引发，属于【非预期】故障
 * 特点：必须记录堆栈、通常触发告警、需人工介入
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/16 21:18
 */
@Getter
public class SystemException extends BaseException {

  @Serial
  private static final long serialVersionUID = 1L;

  public SystemException(IResultCode resultCode) {
    super(resultCode);
  }

  public SystemException(IResultCode resultCode, Object... args) {
    super(resultCode, args);
  }

  public SystemException(IResultCode resultCode, Throwable cause) {
    super(resultCode, cause);
  }

  public SystemException(IResultCode resultCode, Throwable cause, Object... args) {
    super(resultCode, cause, args);
  }

  /**
   * 将任意异常包装为系统异常（自动提取/生成错误码）
   *
   * @param cause 原始异常
   * @return 系统异常
   */
  public static SystemException wrap(Throwable cause, Object... context) {
    if (cause instanceof BusinessException) {
      throw new IllegalArgumentException("Cannot wrap BusinessException as SystemException", cause);
    }

    String code = generateCodeFromCause(cause);
    String originalMessage = BaseException.getMessageOrDefault(cause);

    String prefix = context.length > 0
      ? String.join(" ", Arrays.stream(context)
      .map(String::valueOf)
      .toArray(String[]::new)) + " - "
      : "";

    String message = prefix + originalMessage;
    return new SystemException(code, message, cause);
  }

  private static String generateCodeFromCause(Throwable cause) {
    return switch (cause) {
      case NullPointerException ignored -> "SYS_NPE";
      case SQLException ignored -> "DB_ERROR";
      case TimeoutException ignored -> "SYS_TIMEOUT";
      case IOException ignored -> "IO_ERROR";
      case IllegalArgumentException ignored-> "SYS_ILLEGAL_ARG";
      case SecurityException ignored -> "SYS_SECURITY";
      default -> "SYS_UNKNOWN";
    };
  }

  // —————— 自定义错误码构造 ——————
  public SystemException(String code, String message) {
    super(code, message);
  }

  public SystemException(String code, String message, Object... args) {
    super(code, message, args);
  }

  public SystemException(String code, String message, Throwable cause, Object... args) {
    super(code, message, cause, args);
  }
}
