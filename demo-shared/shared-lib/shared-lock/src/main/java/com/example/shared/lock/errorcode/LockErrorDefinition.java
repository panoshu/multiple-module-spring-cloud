package com.example.shared.lock.errorcode;

import com.example.shared.exception.ErrorDefinition;

/**
 * shared-lock 模块错误码定义。
 * <p>
 * 错误码区间 {@code SHARED.LOCK.0001-SHARED.LOCK.0099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>层级字符串格式：SHARED.LOCK.XXXX（公共基础模块 - shared-lock）</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 * <p>
 * 码段 SHARED.LOCK 原属 shared-cache-starter，随分布式锁代码迁移至 shared-lock。
 *
 * @author panoshu
 * @since 2026/7/24
 */
public enum LockErrorDefinition implements ErrorDefinition {

  GET_LOCK_FAILED("SHARED.LOCK.0001", "获取锁失败"),

  ;

  private final String code;
  private final String message;

  LockErrorDefinition(String code, String message) {
    this.code = code;
    this.message = message;
  }

  @Override
  public String code() {
    return this.code;
  }

  @Override
  public String message() {
    return this.message;
  }
}
