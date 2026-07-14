package com.example.shared.exception;

/**
 * 业务异常
 * 用于预期的业务逻辑阻断（如：用户不存在、库存不足）
 * 全局处理器中通常只打印 WARN 级别，不需要长篇堆栈
 */
public class BusinessException extends BaseException {

  public BusinessException(ErrorDefinition error) {
    super(error);
  }

  public BusinessException(ErrorDefinition error, Throwable cause) {
    super(error, cause);
  }
}
