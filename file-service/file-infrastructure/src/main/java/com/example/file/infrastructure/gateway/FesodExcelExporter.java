package com.example.file.infrastructure.gateway;

import com.example.file.domain.errorcode.FileErrorCodes;
import com.example.file.domain.gateway.ExcelExporter;
import com.example.file.domain.model.valueobject.SplitUnit;
import com.example.shared.exception.SystemException;
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
          //
          // fesod 2.0.2-incubating 限制：模板占位符必须用 {regionName.field} 语法
          // （无前导点），而非 fesod 文档标准的 {.regionName.field}。
          // 根因：ExcelWriteFillExecutor.prepareData() 中 collectPrefixIndex=0 时
          // prefix 保持 null，与 FillWrapper("regionName", list) 的缓存键不匹配，
          // 导致填充静默失败。改用 {regionName.field} 后 collectPrefixIndex>0，
          // prefix 正确设置为 "regionName"，与 FillWrapper 缓存键匹配。
          // 升级 fesod 后可考虑改回 {.regionName.field} 标准语法。
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
      throw new SystemException(FileErrorCodes.EXCEL_EXPORT_FAILED, e);
    }
  }
}
