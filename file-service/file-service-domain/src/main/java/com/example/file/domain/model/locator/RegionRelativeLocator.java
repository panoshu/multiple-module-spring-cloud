package com.example.file.domain.model.locator;

/**
 * 区域内相对定位器 (1-based)
 * row: 1 代表该区域的第 1 行
 * col: 1 代表该区域的第 1 列 (即 A 列)
 */
public record RegionRelativeLocator(
  int row,
  int col
) implements Locator {
}
