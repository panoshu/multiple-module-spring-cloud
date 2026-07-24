package com.example.shared.cache.exception;

import com.example.shared.exception.ErrorDefinition;

/**
 * shared-cache-starter 模块错误码定义。
 * <p>
 * 原 {@code GET_LOCK_FAILED}（13001）已随分布式锁代码迁移至 shared-lock 模块的
 * {@code com.example.shared.lock.errorcode.LockErrorDefinition}。
 * 码段 13001-13099 现归属 shared-lock。
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @since 2026/5/19 14:53
 */
public enum CacheErrorDefinition implements ErrorDefinition {

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
