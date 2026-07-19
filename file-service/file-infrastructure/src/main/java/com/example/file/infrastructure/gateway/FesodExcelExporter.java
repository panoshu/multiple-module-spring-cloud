package com.example.file.infrastructure.gateway;

import com.example.file.domain.gateway.ExcelExporter;
import com.example.file.domain.model.valueobject.SplitUnit;
import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.apache.fesod.sheet.write.metadata.fill.FillConfig;
import org.apache.fesod.sheet.write.metadata.fill.FillWrapper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FesodExcelExporter implements ExcelExporter {

  @Override
  public void export(SplitUnit unit, InputStream templateStream, OutputStream out) {
    try (ExcelWriter writer = FesodSheet.write(out)
            .withTemplate(templateStream)
            .build()) {
      WriteSheet writeSheet = FesodSheet.writerSheet().build();
      Map<String, Object> data = unit.data();

      // 1. 分离简单变量和列表变量
      Map<String, Object> simpleVars = new LinkedHashMap<>();
      for (Map.Entry<String, Object> e : data.entrySet()) {
        Object value = e.getValue();
        if (value instanceof List<?> list) {
          // 列表变量：用 FillWrapper 包装，前缀 = regionName
          // 模板占位符 {regionName.field} 会被替换为列表每项的 field 值
          if (!list.isEmpty()) {
            writer.fill(new FillWrapper(e.getKey(), list),
                FillConfig.builder().forceNewRow(true).build(),
                writeSheet);
          }
        } else {
          simpleVars.put(e.getKey(), value);
        }
      }

      // 2. 填充普通变量
      if (!simpleVars.isEmpty()) {
        writer.fill(simpleVars, writeSheet);
      }
    } catch (Exception e) {
      throw new RuntimeException("Excel 模板填充失败", e);
    }
  }
}
