package com.example.shared.web.core.dto;

import java.util.List;

/**
 * 统一分页响应
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/2/5 15:30
 */

public record PageData<T>(
  int totalCount,
  int currentStart,
  int returnedCount,
  boolean hasMore,
  List<T> items
) {
  public static <T> PageData<T> empty() {
    return new PageData<T>(0, 0, 0, false, List.of());
  }

  public boolean isEmpty() {
    return items == null || items.isEmpty();
  }
}
