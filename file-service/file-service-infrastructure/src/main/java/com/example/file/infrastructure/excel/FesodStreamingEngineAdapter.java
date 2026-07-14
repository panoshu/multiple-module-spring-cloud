package com.example.file.infrastructure.excel;

import com.example.file.domain.gateway.ExcelStreamingEnginePort;
import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.infrastructure.excel.persistence.DynamicSchemaEventListener;
import org.apache.fesod.sheet.FesodSheet;

import java.io.InputStream;
import java.util.function.Consumer;

/**
 * FesodStreamingEngineAdapter
 * 基于 Apache Fesod 的纯流式读取适配器
 */
public class FesodStreamingEngineAdapter implements ExcelStreamingEnginePort {

  @Override
  public void readExcelStream(InputStream in, ExcelSchema schema, Consumer<DataRow> rowProcessor) {
    // 核心：使用 DynamicSchemaEventListener 完全接管解析逻辑
    FesodSheet.read(in, new DynamicSchemaEventListener(schema, rowProcessor))
      .headRowNumber(0) // 设置为 0，因为我们的 Listener 内部动态判断了表头行
      .sheet()
      .doRead();
  }
}
