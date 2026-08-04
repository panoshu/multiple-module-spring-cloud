package com.example.file.domain.service;

import com.example.file.domain.model.enums.KvValuePosition;
import com.example.file.domain.model.enums.RegionType;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.KvStrategy;
import com.example.file.domain.model.valueobject.config.RegionDef;
import com.example.file.domain.model.valueobject.parse.KvRegionResult;
import com.example.file.domain.model.valueobject.parse.RawRow;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegionStateMachineTest {

  @Test
  void should_drive_through_regions_in_order() {
    List<RegionDef> regions = List.of(
      new RegionDef("basic", RegionType.KEY_VALUE, "properties", null,
        new KvStrategy(KvValuePosition.RIGHT, Map.of("enterpriseName", List.of("企业名称")), 3)),
      new RegionDef("detail", RegionType.KEY_VALUE, "properties", null,
        new KvStrategy(KvValuePosition.RIGHT, Map.of("declareDate", List.of("申报日期")), 3))
    );

    List<RawRow> rows = List.of(
      new RawRow(0, Map.of(0, "企业名称", 1, "ABC公司"), false),
      new RawRow(1, Map.of(0, "申报日期", 1, "2026-07-18"), false),
      new RawRow(2, Map.of(), true)
    );
    FakeStream stream = new FakeStream(rows);

    RegionStateMachine sm = new RegionStateMachine(Map.of(
      RegionType.KEY_VALUE, new FakeKvParser()
    ));

    List<RegionParseResult> results = sm.drive(stream, regions, new ParseContext(regions));

    assertThat(results).hasSize(2);
    assertThat(results.get(0).regionName()).isEqualTo("basic");
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
      return rows.get(idx);
    }

    @Override
    public int currentRowIndex() {
      return idx == 0 ? -1 : rows.get(idx - 1).rowIndex();
    }
  }

  static class FakeKvParser implements RegionParser {
    @Override
    public RegionType supportedType() {
      return RegionType.KEY_VALUE;
    }

    @Override
    public RegionParseResult parse(RawRowStream stream, RegionDef regionDef, ParseContext ctx) {
      RawRow row = stream.next();
      Map<String, Object> data = new LinkedHashMap<>();
      data.put(row.cells().values().iterator().next(), row.cells().get(1));
      return new KvRegionResult(regionDef.name(), data);
    }
  }
}
