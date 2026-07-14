package com.example.shared.exception;

/**
 * 领域异常
 * 用于 Domain 层（Entity, Aggregate, DomainService）中的业务规则校验失败。
 * * 特点：
 * 1. 视为业务逻辑的一部分，类似 BusinessException。
 * 2. 传入的 args 默认视为 userMessageArgs（用于格式化返回给前端的信息）。
 * 3. 默认不填充堆栈信息，提升性能。
 */
public class DomainException extends BaseException {

  public DomainException(ErrorDefinition error) {
    super(error);
  }

  public DomainException(ErrorDefinition error, Throwable cause) {
    super(error, cause);
  }

  /**
   * 性能优化：领域异常属于业务流控的一部分，通常不需要昂贵的堆栈追踪
   */
  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
