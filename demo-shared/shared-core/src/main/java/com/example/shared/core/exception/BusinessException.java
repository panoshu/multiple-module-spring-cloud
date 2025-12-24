package com.example.shared.core.exception;

import com.example.shared.core.api.IResultCode;
import lombok.Getter;

import java.io.Serial;

/**
 * 业务异常：由业务规则触发，属于【可预期】的正常流程分支
 * 特点：高频、无需堆栈、通常直接返回前端
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/16 21:17
 */
@Getter
public class BusinessException extends BaseException {

  @Serial
  private static final long serialVersionUID = 1L;

  // —————— 构造函数 ——————

  public BusinessException(IResultCode resultCode) {
    super(resultCode);
  }

  public BusinessException(IResultCode resultCode, Object... args) {
    super(resultCode, args);
  }

  public BusinessException(IResultCode resultCode, Throwable cause) {
    super(resultCode, cause);
  }

  public BusinessException(IResultCode resultCode, Throwable cause, Object... args) {
    super(resultCode, cause, args);
  }

  // —————— 高级：轻量级构造（高频场景优化） ——————
  /**
   * 创建无堆栈、无 suppression 的轻量业务异常（适合高频抛出）
   *
   * @param resultCode 错误码
   * @param args       消息参数
   * @return 轻量异常实例
   */
  public static BusinessException lightweight(IResultCode resultCode, Object... args) {
    BusinessException ex = new BusinessException(
      resultCode.getCode(),
      resultCode.getMessage(),
      null,
      false, false, // 关键：禁用 suppression + 不写堆栈
      args
    );
    ex.setStackTrace(EMPTY_STACK_TRACE);
    return ex;
  }

  private static final StackTraceElement[] EMPTY_STACK_TRACE = new StackTraceElement[0];

  // 私有构造器：仅用于 lightweight
  private BusinessException(String code, String message, Throwable cause,
                            boolean enableSuppression, boolean writableStackTrace,
                            Object... args) {
    super(code, message, cause, enableSuppression, writableStackTrace, args);
  }
}
