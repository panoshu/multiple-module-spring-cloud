package com.example.shared.core.infrastructure;

import com.example.shared.core.api.SystemCode;
import com.example.shared.core.exception.SystemException;

import java.util.function.Supplier;

/**
 * ExternalCallUtils
 *
 * @author <a href="mailto: panoshu@gmail.com">panoshu</a>
 * @since 2025/12/25 21:48
 */
public class ExternalCallUtils {
  public static <T> T executeExternal(Supplier<T> supplier, String errorContext, Object... args) {
    try {
      return supplier.get();
    } catch (Exception e) {
      // 统一包装逻辑
      throw new SystemException(SystemCode.EXTERNAL_SERVICE_ERROR, e)
        .withDetail(errorContext, args);
    }
  }
}
