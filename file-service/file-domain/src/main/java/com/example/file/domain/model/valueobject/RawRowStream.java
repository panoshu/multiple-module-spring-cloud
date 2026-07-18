package com.example.file.domain.model.valueobject;

import com.example.file.domain.model.valueobject.parse.RawRow;

/**
 * 行流游标 SPI。状态机通过 peek/next 拉取行。
 */
public interface RawRowStream {
  boolean hasNext();
  RawRow next();
  RawRow peek();
  int currentRowIndex();
}
