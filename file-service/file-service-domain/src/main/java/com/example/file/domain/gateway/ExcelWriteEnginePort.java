package com.example.file.domain.gateway;

import com.example.file.domain.model.schema.ExcelSchema;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * ExcelWriteEnginePort
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/27 11:28
 */
public interface ExcelWriteEnginePort {
  /**
   * 将业务数据写入为 Excel
   *
   * @param out          输出流
   * @param schema       写入配置 Schema
   * @param discreteData 离散区（主体）数据
   * @param tableData    横表（明细）数据
   */
  void writeExcel(OutputStream out, ExcelSchema schema, Map<String, Object> discreteData, List<Map<String, Object>> tableData);
}
