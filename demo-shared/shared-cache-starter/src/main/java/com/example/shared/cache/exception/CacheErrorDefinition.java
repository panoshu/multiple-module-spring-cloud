package com.example.shared.cache.exception;

import com.example.shared.exception.ErrorDefinition;

public enum CacheErrorDefinition implements ErrorDefinition {

  GET_LOCK_FAILED("99999", "获取锁失败"),

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
