package com.example.file.infrastructure.errorcode;

import com.example.shared.exception.ErrorDefinition;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

/**
 * FileErrorCode
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/2/9 22:48
 */
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public enum FileErrorCode implements ErrorDefinition {

  FILE_IO_ERROR("99910", "文件IO错误"),
  FILE_NOT_FOUND("99911", "文件未找到"),

  ;

  private final String code;
  private final String message;

  public String code() {
    return this.code;
  }

  public String message() {
    return this.message;
  }
}
