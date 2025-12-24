package com.example.shared.core.api;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * BusinessCode
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 12:28
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum BusinessCode implements IResultCode{

  VALIDATION_ERROR("10000", "参数校验错误: {}"),

  ;

  private final String code;
  private final String message;
}
