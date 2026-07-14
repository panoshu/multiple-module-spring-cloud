package com.example.file.domain.service;

import com.example.file.domain.model.enums.DataType;
import com.example.file.domain.model.enums.FieldType;
import com.example.file.domain.model.rule.DateRule;
import com.example.file.domain.model.rule.NumberRule;
import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.FieldConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FormValidationServiceTest {

  private FormValidationService validationService;

  @BeforeEach
  void setUp() {
    validationService = new FormValidationService();
  }

  @Test
  void testRequiredField() {
    FieldConfig config = new FieldConfig("name", FieldType.DATA_FIELD, "姓名", DataType.STRING, "", true, null, null, null);
    DataRow row = new DataRow(1, new HashMap<>()); // 缺失 name

    validationService.validateRow(row, List.of(config));

    assertTrue(row.hasErrors());
    assertEquals("不能为空", row.errors().getFirst().message());
  }

  @Test
  void testNumberRule() {
    NumberRule rule = new NumberRule(new BigDecimal("100"), new BigDecimal("1000"));
    FieldConfig config = new FieldConfig("score", FieldType.DATA_FIELD, "分数", DataType.NUMBER, "", true, List.of(rule), null, null);

    // 测试小于最小值
    DataRow row1 = new DataRow(1, new HashMap<>(Map.of("score", "50")));
    validationService.validateRow(row1, List.of(config));
    assertTrue(row1.hasErrors());
    assertTrue(row1.errors().getFirst().message().contains("不能小于"));

    // 测试正常值
    DataRow row2 = new DataRow(2, new HashMap<>(Map.of("score", "500")));
    validationService.validateRow(row2, List.of(config));
    assertFalse(row2.hasErrors());
  }

  @Test
  void testDateRuleCrossField() {
    // start_date 必须早于 end_date
    DateRule rule = new DateRule(null, null, "end_date", null);
    FieldConfig config = new FieldConfig("start_date", FieldType.DATA_FIELD, "开始日期", DataType.DATE, "", true, List.of(rule), null, null);

    // 准备数据：开始时间晚于结束时间 (错误)
    Map<String, Object> data = new HashMap<>();
    data.put("start_date", "2023-10-05");
    data.put("end_date", "2023-10-01");
    DataRow row = new DataRow(1, data);

    validationService.validateRow(row, List.of(config));

    assertTrue(row.hasErrors());
    assertTrue(row.errors().getFirst().message().contains("早于 end_date"));
  }
}
