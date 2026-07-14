package com.example.shared.web.core.dto;


import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 统一分页请求
 */
public record PageQuery(
  @Min(MIN_START_POS)
  int startPos,

  @Min(MIN_PAGE_SIZE)
  @Max(MAX_PAGE_SIZE)
  int pageSize
) {

  public static final int MIN_START_POS = 0;
  public static final int MIN_PAGE_SIZE = 5;
  public static final int MAX_PAGE_SIZE = 100;
  public static final int DEFAULT_PAGE_SIZE = 10;

  public static PageQuery of(int startPos, int pageSize) {
    return new PageQuery(startPos, pageSize);
  }

  public static PageQuery of(int startPos) {
    return new PageQuery(startPos, DEFAULT_PAGE_SIZE);
  }

  public static PageQuery firstPage(int pageSize) {
    return new PageQuery(MIN_START_POS, pageSize);
  }

}
