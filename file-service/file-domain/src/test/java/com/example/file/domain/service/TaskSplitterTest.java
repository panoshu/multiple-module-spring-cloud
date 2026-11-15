package com.example.file.domain.service;

import com.example.file.domain.model.enums.SplitKeyType;
import com.example.file.domain.model.parse.valueobject.SplitUnit;
import com.example.file.domain.model.parse.valueobject.config.SplitConfig;
import com.example.file.domain.model.parse.valueobject.config.SplitKeyDef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskSplitterTest {

  @Test
  @SuppressWarnings("unchecked")
  void should_split_table_rows_by_business_key() {
    SplitConfig config = new SplitConfig(
      List.of(),
      new SplitKeyDef("applicant", "items.applicant", SplitKeyType.FIELD_VALUE),
      null, null, null, false, 0);
    Map<String, Object> data = Map.of(
      "applicant", "张三",
      "items", List.of(
        Map.of("code", "A1", "applicant", "张三", "qty", 1),
        Map.of("code", "A2", "applicant", "李四", "qty", 2),
        Map.of("code", "A3", "applicant", "张三", "qty", 3)));
    TaskSplitter splitter = new TaskSplitter();

    List<SplitUnit> units = splitter.split(data, config);

    assertThat(units).hasSize(2);
    SplitUnit zhangsan = units.stream().filter(u -> u.splitKey().equals("张三")).findFirst().orElseThrow();
    assertThat(zhangsan.splitKey()).isEqualTo("张三");
    assertThat((List<Map<String, Object>>) zhangsan.data().get("items")).hasSize(2);
  }

  @Test
  void should_split_by_property_value_when_source_path_has_no_dot() {
    SplitConfig config = new SplitConfig(
      List.of(),
      new SplitKeyDef("planNo", "planNo", SplitKeyType.FIELD_VALUE),
      null, null, null, false, 0);
    Map<String, Object> data = Map.of("planNo", "PLAN-2026-001", "employees", List.of());

    List<SplitUnit> units = new TaskSplitter().split(data, config);

    assertThat(units).hasSize(1);
    assertThat(units.get(0).splitKey()).isEqualTo("PLAN-2026-001");
  }
}
