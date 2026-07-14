package com.example.shared.exception;

/**
 * 系统异常
 * 用于非预期的系统级故障（如：数据库宕机、第三方服务超时）
 * 全局处理器中必须打印 ERROR 级别及完整堆栈，且前端需脱敏
 */
public class SystemException extends BaseException {

  public SystemException(ErrorDefinition error) {
    super(error);
  }

  public SystemException(ErrorDefinition error, Throwable cause) {
    super(error, cause);
  }
}
