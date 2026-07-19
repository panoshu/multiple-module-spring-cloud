package com.example.file.domain.service;

import com.example.file.domain.model.enums.HeaderMatching;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.enums.TableMatchBy;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.DataEndRule;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.config.TableStrategy;
import com.example.file.domain.model.valueobject.parse.RawRow;
import com.example.file.domain.model.valueobject.parse.TableRegionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TableRegionParserTest {

  @Test
  void should_parse_table_with_header_and_data_rows() {
    RegionDef def = new RegionDef("items", RegionType.TABLE, null, null,
        new TableStrategy(
            1, 0, TableMatchBy.HEADER_NAME,
            Map.of("code", List.of("商品编码"), "name", List.of("商品名称"), "qty", List.of("数量")),
            HeaderMatching.STRICT, 100, null));
    RawRowStream stream = new FakeStream(List.of(
        RawRow.of(0, Map.of(0, "商品编码", 1, "商品名称", 2, "数量"), false),
        RawRow.of(1, Map.of(0, "A1", 1, "苹果", 2, "10"), false),
        RawRow.of(2, Map.of(0, "A2", 1, "香蕉", 2, "20"), false),
        RawRow.of(3, Map.of(), true)));
    TableRegionParser parser = new TableRegionParser();

    TableRegionResult result = (TableRegionResult) parser.parse(stream, def, new ParseContext(List.of(def)));

    assertThat(result.headers()).containsExactly("code", "name", "qty");
    assertThat(result.rows()).hasSize(2);
    assertThat(result.rows().get(0)).containsEntry("code", "A1");
    assertThat(result.rows().get(1)).containsEntry("qty", "20");
  }

  @Test
  void should_use_headerNameRow_to_select_header_row() {
    RegionDef def = new RegionDef("items", RegionType.TABLE, null, null,
        new TableStrategy(
            3, 1, TableMatchBy.HEADER_NAME,
            Map.of("seq", List.of("XH"), "name", List.of("XM")),
            HeaderMatching.STRICT, 100, null));
    RawRowStream stream = new FakeStream(List.of(
        RawRow.of(0, Map.of(0, "XH", 1, "XM"), false),
        RawRow.of(1, Map.of(0, "基本信息"), false),
        RawRow.of(2, Map.of(0, "序号*", 1, "个人姓名*"), false),
        RawRow.of(3, Map.of(0, "1", 1, "张三"), false),
        RawRow.of(4, Map.of(), true)));
    TableRegionParser parser = new TableRegionParser();

    TableRegionResult result = (TableRegionResult) parser.parse(stream, def, new ParseContext(List.of(def)));

    assertThat(result.headers()).containsExactly("seq", "name");
    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0)).containsEntry("seq", "1");
    assertThat(result.rows().get(0)).containsEntry("name", "张三");
  }

  @Test
  void should_stop_at_dataEnd_marker() {
    RegionDef def = new RegionDef("items", RegionType.TABLE, null, null,
        new TableStrategy(
            1, 0, TableMatchBy.HEADER_NAME,
            Map.of("code", List.of("code")),
            HeaderMatching.STRICT, 100,
            new DataEndRule(List.of("结束"), 1)));
    RawRowStream stream = new FakeStream(List.of(
        RawRow.of(0, Map.of(0, "code"), false),
        RawRow.of(1, Map.of(0, "A1"), false),
        RawRow.of(2, Map.of(0, "结束", 1, "说明文字"), false),
        RawRow.of(3, Map.of(0, "A2"), false)));
    TableRegionParser parser = new TableRegionParser();

    TableRegionResult result = (TableRegionResult) parser.parse(stream, def, new ParseContext(List.of(def)));

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0)).containsEntry("code", "A1");
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
