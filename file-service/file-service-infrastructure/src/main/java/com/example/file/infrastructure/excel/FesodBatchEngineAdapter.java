package com.example.file.infrastructure.excel;

import com.example.file.domain.gateway.ExcelEnginePort;
import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.ValidationError;
import com.example.file.infrastructure.excel.persistence.DynamicSchemaEventListener;
import org.apache.fesod.sheet.FesodSheet;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * FesodBatchEngineAdapter
 * 基于 Apache Fesod 的全量内存读写适配器
 */
public class FesodBatchEngineAdapter implements ExcelEnginePort {

  @Override
  public List<DataRow> readExcel(InputStream in, ExcelSchema schema) {
    List<DataRow> allRows = new ArrayList<>();

    // 复用流式 Listener，将数据收集到 List 中
    FesodSheet.read(in, new DynamicSchemaEventListener(schema, allRows::add))
      .headRowNumber(0)
      .sheet()
      .doRead();

    return allRows;
  }

  @Override
  public void writeExcel(OutputStream out, ExcelSchema schema, List<DataRow> data, List<ValidationError> errors) {
    // 注意：这里的 writeExcel 是早期接口定义中用于全量写出的。
    // 由于我们后来引入了专门的 ExcelWriteEnginePort (FesodWriteEngineAdapter) 处理白板创建，
    // 并在 FileStoragePort (OssFileStorageAdapter) 中处理了错误回写，
    // 如果您的架构中不再单独调用此全量方法，可以抛出 UnsupportedOperationException 或保留为空。
    throw new UnsupportedOperationException("请使用 ExcelWriteEnginePort 或 FileStoragePort.uploadErrorExcel");
  }
}
