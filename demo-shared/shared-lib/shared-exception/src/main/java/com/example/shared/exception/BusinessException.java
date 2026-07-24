package com.example.shared.exception;

/**
 * 业务异常
 * <p>
 * 用于预期的业务逻辑阻断（如：用户不存在、库存不足）。全局处理器中通常只打印 WARN 级别，不需要长篇堆栈。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/19 14:53
 */
public class BusinessException extends BaseException {

  public BusinessException(ErrorDefinition error) {
    super(error);
  }

  public BusinessException(ErrorDefinition error, Throwable cause) {
    super(error, cause);
  }

  @Override
  public BusinessException withUserDetail(String detail) {
    super.withUserDetail(detail);
    return this;
  }

  @Override
  public BusinessException withLogDetail(String detail) {
    super.withLogDetail(detail);
    return this;
  }

  @Override
  public BusinessException withContext(String key, Object value) {
    super.withContext(key, value);
    return this;
  }
}
