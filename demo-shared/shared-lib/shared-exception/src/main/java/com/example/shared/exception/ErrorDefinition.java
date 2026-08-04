package com.example.shared.exception;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/19 14:53
 */
public interface ErrorDefinition {
  String getCode();

  String getMessage();

  default String errorInfo() {
    return "[" + this.getCode() + "] " + this.getMessage();
  }
}
