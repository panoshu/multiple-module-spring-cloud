package com.example.file.domain.service;

import com.example.file.domain.model.enums.*;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.*;
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

  @Test
  void should_leave_trigger_row_for_next_region_when_no_dataEnd() {
    // 回归测试 (C1)：表格无 dataEnd marker，数据行后紧跟下一个 KV region 的 trigger 行。
    // 旧实现：先 stream.next() 再 isNextRegionTrigger 检查 → trigger 行被消费，下一个 region 漏掉首行。
    // 新实现：先 isNextRegionTrigger 检查再 stream.next() → trigger 行留在流中，下一个 region 正确消费。
    RegionDef tableDef = new RegionDef("items", RegionType.TABLE, "items",
      new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 2),
      new TableStrategy(
        1, 0, TableMatchBy.HEADER_NAME,
        Map.of("code", List.of("code")),
        HeaderMatching.STRICT, 100, null));
    RegionDef kvDef = new RegionDef("footer", RegionType.KEY_VALUE, "properties",
      new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 1),
      new KvStrategy(KvValuePosition.RIGHT, Map.of("filler", List.of("填表人")), 3));
    List<RegionDef> regions = List.of(tableDef, kvDef);
    ParseContext ctx = new ParseContext(regions);

    RawRowStream stream = new FakeStream(List.of(
      RawRow.of(0, Map.of(0, "code"), false),
      RawRow.of(1, Map.of(0, "A1"), false),
      RawRow.of(2, Map.of(0, "A2"), false),
      RawRow.of(3, Map.of(0, "填表人", 1, "张三"), false),
      RawRow.of(4, Map.of(), true)));

    // 模拟 RegionStateMachine.drive 行为：先 enterRegion(0)，再 parse
    ctx.enterRegion(0);
    TableRegionParser parser = new TableRegionParser();
    TableRegionResult result = (TableRegionResult) parser.parse(stream, tableDef, ctx);

    // 表格 region 应得 2 行数据，trigger 行（"填表人 张三"）不应被消费
    assertThat(result.rows()).hasSize(2);
    assertThat(result.rows().get(0)).containsEntry("code", "A1");
    assertThat(result.rows().get(1)).containsEntry("code", "A2");

    // trigger 行应留在流中，供下一个 region 消费
    assertThat(stream.hasNext()).isTrue();
    RawRow triggerRow = stream.peek();
    assertThat(triggerRow.cells().get(0)).isEqualTo("填表人");
  }

  static class FakeStream implements RawRowStream {
    private final List<RawRow> rows;
    private int idx = 0;

    FakeStream(List<RawRow> rows) {
      this.rows = rows;
    }

    @Override
    public boolean hasNext() {
      return idx < rows.size();
    }

    @Override
    public RawRow next() {
      return rows.get(idx++);
    }

    @Override
    public RawRow peek() {
      return idx < rows.size() ? rows.get(idx) : rows.get(rows.size() - 1);
    }

    @Override
    public int currentRowIndex() {
      return idx == 0 ? -1 : rows.get(idx - 1).rowIndex();
    }
  }
}
