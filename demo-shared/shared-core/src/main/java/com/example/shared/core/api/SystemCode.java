package com.example.shared.core.api;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * SystemCode
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/24 12:28
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum SystemCode implements IResultCode{
  SUCCESS("00000", "SUCCESS"),
  EXTERNAL_SERVICE_ERROR("99980", "外部调用错误: {}"),
  SYS_UNKNOWN_ERROR("99998", "系统异常，请联系管理员"),
  SYS_INTERNAL_ERROR("99999", "系统繁忙，请稍后重试"),

  ;

  private final String code;
  private final String message;

  // public String formatMessage(Object... args) {
  //   return IResultCode.super.formatMessage(args);
  // }
}
