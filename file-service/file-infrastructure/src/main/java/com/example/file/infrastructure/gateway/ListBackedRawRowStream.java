package com.example.file.infrastructure.gateway;

import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.parse.RawRow;

import java.util.List;

public class ListBackedRawRowStream implements RawRowStream {
  private final List<RawRow> rows;
  private int index = 0;

  public ListBackedRawRowStream(List<RawRow> rows) {
    this.rows = rows;
  }

  @Override
  public boolean hasNext() {
    return index < rows.size();
  }

  @Override
  public RawRow next() {
    return rows.get(index++);
  }

  @Override
  public RawRow peek() {
    return rows.get(index);
  }

  @Override
  public int currentRowIndex() {
    return index > 0 ? rows.get(index - 1).rowIndex() : 0;
  }
}
