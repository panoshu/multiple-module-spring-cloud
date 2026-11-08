package com.example.shared.page;

import java.io.Serializable;

/**
 * description
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/2/5 11:17
 */
public record PageInfo(
  int totalCount,
  int currentStart,
  int returnedCount,
  boolean hasMore
) implements Serializable {
  public static PageInfo empty() {
    return new PageInfo(0, 0, 0, false);
  }

  public static PageInfo of(int total, Pagination pagination, int actualReturned) {
    boolean hasMore = pagination.startPos() + actualReturned < total;
    return new PageInfo(total, pagination.startPos(), actualReturned, hasMore);
  }
}
