package com.example.file.infrastructure.gateway;

import com.example.file.domain.gateway.ExcelParser;
import com.example.file.domain.model.enums.*;
import com.example.file.domain.model.valueobject.RawRowStream;
import com.example.file.domain.model.valueobject.config.*;
import com.example.file.domain.model.valueobject.parse.KvRegionResult;
import com.example.file.domain.model.valueobject.parse.RawRow;
import com.example.file.domain.model.valueobject.parse.RegionParseResult;
import com.example.file.domain.model.valueobject.parse.TableRegionResult;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelParserImplTest {

  private static final String EXCEL_PATH =
    Path.of("docs", "excel", "示例表单.xlsx").toString();

  private final ExcelParser parser = new ExcelParserImpl();

  @Test
  void openStream_读取所有行() throws Exception {
    try (InputStream is = new FileInputStream(EXCEL_PATH)) {
      RawRowStream stream = parser.openStream(is);

      int count = 0;
      while (stream.hasNext()) {
        RawRow row = stream.next();
        if (count == 0) {
          // idx=0 是企业计划编号 KV 行（headRowNumber=0 不跳过首行）
          assertThat(row.isBlank()).isFalse();
          assertThat(row.cells().get(0)).isEqualTo("企业计划编号：");
        }
        if (count == 1) {
          // idx=1 是企业客户号 KV 行
          assertThat(row.isBlank()).isFalse();
          assertThat(row.cells().get(0)).isEqualTo("企业客户号：");
        }
        if (count == 3) {
          // idx=3 是 XH/XM/ZJLX/ZJHM 代码表头行（42 cells，非 blank）
          assertThat(row.isBlank()).isFalse();
          assertThat(row.cells().get(0)).isEqualTo("XH");
        }
        count++;
      }
      assertThat(count).isEqualTo(14);
    }
  }

  @Test
  void parse_KV区域_多组label_value() throws Exception {
    List<RegionDef> regions = List.of(
      new RegionDef("basic_info", RegionType.KEY_VALUE, "properties",
        new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 2),
        new KvStrategy(KvValuePosition.RIGHT,
          Map.of(
            "customerNo", List.of("企业客户号："),
            "customerName", List.of("企业客户名称：")),
          2)));
    try (InputStream is = new FileInputStream(EXCEL_PATH)) {
      List<RegionParseResult> results = parser.parse(is, regions);

      assertThat(results).hasSize(1);
      KvRegionResult kv = (KvRegionResult) results.get(0);
      // 实际文件 idx=0 行包含 2 组 KV：企业客户号=000234, 企业客户名称=客户A
      assertThat(kv.data()).containsEntry("customerNo", "000234");
      assertThat(kv.data()).containsEntry("customerName", "客户A");
    }
  }

  @Test
  void parse_表格区域_headerNameRow_1_并映射标准字段() throws Exception {
    List<RegionDef> regions = List.of(
      new RegionDef("employee_list", RegionType.TABLE, "employees",
        new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 5),
        new TableStrategy(
          3, 1, TableMatchBy.HEADER_NAME,
          Map.of(
            "seq", List.of("XH"),
            "name", List.of("XM"),
            "idType", List.of("ZJLX"),
            "idNo", List.of("ZJHM")),
          HeaderMatching.STRICT, 0,
          new DataEndRule(List.of("结束"), 1))));
    try (InputStream is = new FileInputStream(EXCEL_PATH)) {
      List<RegionParseResult> results = parser.parse(is, regions);

      assertThat(results).hasSize(1);
      TableRegionResult table = (TableRegionResult) results.get(0);
      assertThat(table.headers()).contains("seq", "name", "idType", "idNo");
      assertThat(table.rows()).hasSize(3);
      assertThat(table.rows().get(0)).containsEntry("seq", "1");
      assertThat(table.rows().get(0)).containsEntry("name", "张内Aa01");
      assertThat(table.rows().get(0)).containsEntry("idType", "身份证");
    }
  }

  @Test
  void parse_表格区域_结束标记停止() throws Exception {
    List<RegionDef> regions = List.of(
      new RegionDef("employee_list", RegionType.TABLE, "employees",
        new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 5),
        new TableStrategy(
          3, 1, TableMatchBy.HEADER_NAME,
          Map.of("seq", List.of("XH")),
          HeaderMatching.STRICT, 0,
          new DataEndRule(List.of("结束"), 1))));
    try (InputStream is = new FileInputStream(EXCEL_PATH)) {
      List<RegionParseResult> results = parser.parse(is, regions);

      TableRegionResult table = (TableRegionResult) results.get(0);
      assertThat(table.rows()).hasSize(3);
      assertThat(table.rows().get(0).get("seq")).isEqualTo("1");
      assertThat(table.rows().get(2).get("seq")).isEqualTo("3");
    }
  }

  @Test
  void parse_KV区域2_填表人() throws Exception {
    List<RegionDef> regions = List.of(
      new RegionDef("filler_info", RegionType.KEY_VALUE, "properties",
        new RegionTrigger(TriggerMatchType.HEADER_SNIFF, 1),
        new KvStrategy(KvValuePosition.RIGHT,
          Map.of(
            "filler", List.of("填表人:"),
            "reviewer", List.of("复核人：")),
          1)));
    try (InputStream is = new FileInputStream(EXCEL_PATH)) {
      List<RegionParseResult> results = parser.parse(is, regions);

      assertThat(results).hasSize(1);
      KvRegionResult kv = (KvRegionResult) results.get(0);
      assertThat(kv.data()).containsEntry("filler", "张三");
      assertThat(kv.data()).containsEntry("reviewer", "李四");
    }
  }
}
