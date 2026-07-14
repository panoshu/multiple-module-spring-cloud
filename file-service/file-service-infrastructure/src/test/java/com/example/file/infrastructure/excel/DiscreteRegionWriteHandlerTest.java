package com.example.file.infrastructure.excel;

import com.example.file.domain.model.enums.FieldType;
import com.example.file.domain.model.locator.AbsoluteLocator;
import com.example.file.domain.model.region.DiscreteRegionConfig;
import com.example.file.domain.model.schema.FieldConfig;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("基础设施: 离散区绘制拦截器测试 (纯内存集成)")
class DiscreteRegionWriteHandlerTest {

  @Test
  @DisplayName("测试离散区自绘：应根据坐标精准绘制静态文本和动态数据")
  void testDiscreteRegionWriting() throws Exception {
    // 1. 准备离散区配置
    // A1 绝对定位写入静态文本 "企业计划："
    FieldConfig titleConfig = new FieldConfig("planTitle", FieldType.TEXT_FIELD, "企业计划：", null, null, false, null, new AbsoluteLocator("A1"), null);
    // B1 绝对定位写入动态数据
    FieldConfig planConfig = new FieldConfig("planNum", FieldType.DATA_FIELD, "计划编号", null, null, false, null, new AbsoluteLocator("B1"), null);

    // 锚点定位测试（假设我们要测试锚点向右偏移，简化起见这里暂用另一个绝对坐标测试骨架，锚点需要 sheet 预先有数据，逻辑类似）
    DiscreteRegionConfig discreteRegion = new DiscreteRegionConfig("d1", 1, List.of(titleConfig, planConfig));

    // 2. 准备业务数据
    Map<String, Object> discreteData = Map.of("planNum", "PLAN-2026-X1");

    // 3. 在内存中触发 Fesod 写入，并挂载我们的 Handler
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    FesodSheet.write(out)
      .registerWriteHandler(new DiscreteRegionWriteHandler(List.of(discreteRegion), discreteData))
      .sheet("TestSheet")
      .doWrite(List.of()); // 传空 List 表示只画离散区，不写明细横表

    // 4. 使用原生 Apache POI 读取生成的 Excel 字节流进行精准断言
    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
      Sheet sheet = workbook.getSheet("TestSheet");
      assertNotNull(sheet, "应该成功创建 Sheet");

      // 验证 A1 单元格 (Row 0, Col 0) 是否为静态文本
      String a1Value = sheet.getRow(0).getCell(0).getStringCellValue();
      assertEquals("企业计划：", a1Value);

      // 验证 B1 单元格 (Row 0, Col 1) 是否为动态数据
      String b1Value = sheet.getRow(0).getCell(1).getStringCellValue();
      assertEquals("PLAN-2026-X1", b1Value);
    }
  }
}
