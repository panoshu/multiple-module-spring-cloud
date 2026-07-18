package com.example.file.domain.service;

import com.example.file.domain.model.enums.KvValuePosition;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.*;
import com.example.file.domain.model.valueobject.parse.KvRegionResult;
import com.example.file.domain.model.valueobject.parse.RawRow;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeyValueRegionParserTest {

  @Test
  void should_parse_RIGHT_layout() {
    RegionDef def = new RegionDef("basic", RegionType.KEY_VALUE, "properties", null,
        new KvStrategy(KvValuePosition.RIGHT, Map.of("enterpriseName", List.of("企业名称")), 3));
    FakeStream stream = new FakeStream(List.of(
        new RawRow(0, Map.of(0, "企业名称", 1, "ABC公司"), false),
        new RawRow(1, Map.of(), true),
        new RawRow(2, Map.of(), true),
        new RawRow(3, Map.of(), true)
    ));

    KeyValueRegionParser parser = new KeyValueRegionParser();
    KvRegionResult result = (KvRegionResult) parser.parse(stream, def, new ParseContext(List.of(def)));

    assertThat(result.data()).containsEntry("enterpriseName", "ABC公司");
  }

  @Test
  void should_exit_on_max_blank_rows() {
    RegionDef def = new RegionDef("basic", RegionType.KEY_VALUE, "properties", null,
        new KvStrategy(KvValuePosition.RIGHT, Map.of("k", List.of("键")), 2));
    FakeStream stream = new FakeStream(List.of(
        new RawRow(0, Map.of(0, "键", 1, "值1"), false),
        new RawRow(1, Map.of(), true),
        new RawRow(2, Map.of(), true),
        new RawRow(3, Map.of(0, "key", 1, "val"), false)
    ));

    KeyValueRegionParser parser = new KeyValueRegionParser();
    KvRegionResult result = (KvRegionResult) parser.parse(stream, def, new ParseContext(List.of(def)));

    assertThat(result.data()).containsEntry("k", "值1");
    assertThat(result.data()).hasSize(1);
  }

  static class FakeStream implements RawRowStream {
    private final List<RawRow> rows;
    private int idx = 0;
    FakeStream(List<RawRow> rows) { this.rows = rows; }
    @Override public boolean hasNext() { return idx < rows.size(); }
    @Override public RawRow next() { return rows.get(idx++); }
    @Override public RawRow peek() { return idx < rows.size() ? rows.get(idx) : rows.get(rows.size() - 1); }
    @Override public int currentRowIndex() { return idx == 0 ? -1 : rows.get(idx - 1).rowIndex(); }
  }
}
