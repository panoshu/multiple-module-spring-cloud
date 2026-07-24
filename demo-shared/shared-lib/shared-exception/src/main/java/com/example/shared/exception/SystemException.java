package com.example.shared.exception;

/**
 * 系统异常
 * <p>
 * 用于非预期的系统级故障（如：数据库宕机、第三方服务超时）。全局处理器中必须打印 ERROR 级别及完整堆栈，且前端需脱敏。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/19 14:53
 */
public class SystemException extends BaseException {

  public SystemException(ErrorDefinition error) {
    super(error);
  }

  public SystemException(ErrorDefinition error, Throwable cause) {
    super(error, cause);
  }

  @Override
  public SystemException withUserDetail(String detail) {
    super.withUserDetail(detail);
    return this;
  }

  @Override
  public SystemException withLogDetail(String detail) {
    super.withLogDetail(detail);
    return this;
  }

  @Override
  public SystemException withContext(String key, Object value) {
    super.withContext(key, value);
    return this;
  }
}
