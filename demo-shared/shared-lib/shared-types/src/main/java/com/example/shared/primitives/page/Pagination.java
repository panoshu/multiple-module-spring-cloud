package com.example.shared.primitives.page;

import java.io.Serializable;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/2/5 11:19
 */
public record Pagination(
  int startPos,   // 起始记录号（从0开始）
  int pageSize    // 每页记录数
) implements Serializable {
  public static final int DEFAULT_START_POS = 0;
  public static final int DEFAULT_PAGE_SIZE = 10;
  public static final int MIN_PAGE_SIZE = 5;
  public static final int MAX_PAGE_SIZE = 100;

  public Pagination {
    if (pageSize < MIN_PAGE_SIZE || pageSize > MAX_PAGE_SIZE) {
      throw new IllegalArgumentException("分页大小必须在 %d-%d 之间".formatted(MIN_PAGE_SIZE, MAX_PAGE_SIZE));
    }
  }

  public static Pagination of(int startPos) {
    return new Pagination(startPos, DEFAULT_PAGE_SIZE);
  }

  public static Pagination of(int startPos, int pageSize) {
    return new Pagination(startPos, pageSize);
  }

  public Pagination next() {
    return new Pagination(startPos + pageSize, pageSize);
  }
}
