package com.example.shared.cache.exception;

import com.example.shared.exception.ErrorDefinition;

/**
 * shared-cache-starter 模块错误码定义。
 * <p>
 * 错误码区间 {@code 13001-13099}，遵循 {@code 08-错误码规范.md}：
 * <ul>
 *   <li>5 位纯数字，首位 1 表示公共基础模块，2-3 位 30 表示 shared-cache-starter</li>
 *   <li>消息使用纯文本，禁止 {} 占位符和方括号前缀</li>
 *   <li>动态上下文通过 {@code BaseException.withUserDetail()/withContext()} 附加</li>
 * </ul>
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @since 2026/5/19 14:53
 */
public enum CacheErrorDefinition implements ErrorDefinition {

  GET_LOCK_FAILED("13001", "获取锁失败"),

  ;

  private final String code;
  private final String message;

  CacheErrorDefinition(String code, String message) {
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
