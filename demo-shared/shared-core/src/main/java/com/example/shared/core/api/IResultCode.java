package com.example.shared.core.api;

import org.jspecify.annotations.NonNull;

import java.text.MessageFormat;

/**
 * 统一状态码接口
 * 所有业务模块的错误码 Enum 都要实现这个接口
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/16 21:15
 */
public interface IResultCode {
  @NonNull String getCode();
  @NonNull String getMessage();

  // 可选：提供消息格式化能力（与 BaseException 的 args 配合）
  default String formatMessage(Object... args) {
    if (args == null || args.length == 0) {
      return getMessage();
    }
    // JDK 21 推荐用 FormattedString + MessageFormat（或简单 {} 替换）
    return MessageFormat.format(getMessage(), args);
  }
}
