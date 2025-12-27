package com.example.shared.core.exception;

import com.example.shared.core.api.IResultCode;
import lombok.Getter;

import java.io.IOException;
import java.io.Serial;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

  // 使用 Map 替代硬编码 Switch，支持动态注册
  private static final Map<Class<? extends Throwable>, String> EXCEPTION_CODE_MAP = new ConcurrentHashMap<>();

  static {
    // 注册默认映射
    registerExceptionCode(NullPointerException.class, "SYS_NPE");
    registerExceptionCode(SQLException.class, "DB_ERROR");
    registerExceptionCode(TimeoutException.class, "SYS_TIMEOUT");
    registerExceptionCode(IOException.class, "IO_ERROR");
    registerExceptionCode(IllegalArgumentException.class, "SYS_ILLEGAL_ARG");
    registerExceptionCode(SecurityException.class, "SYS_SECURITY");
  }

  /**
   * 开放扩展点：允许业务模块注册自定义异常对应的错误码
   */
  public static void registerExceptionCode(Class<? extends Throwable> exceptionClass, String code) {
    EXCEPTION_CODE_MAP.put(exceptionClass, code);
  }

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

    return new SystemException(code, prefix + originalMessage, cause);
  }

  private static String generateCodeFromCause(Throwable cause) {
    if (cause == null) return "SYS_UNKNOWN";

    // 1. 精确匹配
    String code = EXCEPTION_CODE_MAP.get(cause.getClass());
    if (code != null) return code;

    // 2. 遍历继承关系匹配 (如 FileNotFoundException -> IOException)
    for (Map.Entry<Class<? extends Throwable>, String> entry : EXCEPTION_CODE_MAP.entrySet()) {
      if (entry.getKey().isAssignableFrom(cause.getClass())) {
        return entry.getValue();
      }
    }

    return "SYS_UNKNOWN";
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
