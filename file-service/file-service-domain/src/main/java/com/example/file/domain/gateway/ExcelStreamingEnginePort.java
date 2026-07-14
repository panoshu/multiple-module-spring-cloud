package com.example.file.domain.gateway;

import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.ExcelSchema;

import java.io.InputStream;
import java.util.function.Consumer;

// ==========================================
// 1. 流式 Excel 解析防腐层
// ==========================================
public interface ExcelStreamingEnginePort {
  // 传入 Consumer 进行回调处理，或者返回一个 JDK Stream<DataRow>
  // 底层实现应使用 EasyExcel 的 ReadListener 或 POI 的 SAX 解析
  void readExcelStream(InputStream in, ExcelSchema schema, Consumer<DataRow> rowProcessor);
}
