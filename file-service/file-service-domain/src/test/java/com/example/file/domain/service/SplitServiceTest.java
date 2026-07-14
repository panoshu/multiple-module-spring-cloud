package com.example.file.domain.service;

import com.example.file.domain.model.enums.BizType;
import com.example.file.domain.model.enums.SchemaType;
import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.SplitStrategyConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitServiceTest {

  private final SplitService splitService = new SplitService();

  @Test
  void testSplitDataOne() {
    SplitStrategyConfig config = new SplitStrategyConfig(true, List.of("deptId"), "${bizType}_${splitValue}.json");
    ExcelSchema schema = new ExcelSchema("s1", BizType.EMPLOYEE_ONBOARDING, SchemaType.READ, null, null, null, null, config, null, List.of());

    DataRow row1 = new DataRow(1, new HashMap<>(Map.of("name", "Alice", "deptId", "DEV")));
    DataRow row2 = new DataRow(2, new HashMap<>(Map.of("name", "Bob", "deptId", "DEV")));
    DataRow row3 = new DataRow(3, new HashMap<>(Map.of("name", "Charlie", "deptId", "HR")));

    Map<String, List<Map<String, Object>>> result = splitService.splitData(List.of(row1, row2, row3), schema);

    assertEquals(2, result.size(), "应该生成两个文件");
    assertTrue(result.containsKey("EMPLOYEE_ONBOARDING_DEV.json"));
    assertTrue(result.containsKey("EMPLOYEE_ONBOARDING_HR.json"));

    // 验证 _rowIndex 被自动注入
    assertEquals(2, result.get("EMPLOYEE_ONBOARDING_DEV.json").size());
    assertEquals(3, result.get("EMPLOYEE_ONBOARDING_HR.json").getFirst().get("_rowIndex"));
  }
}
