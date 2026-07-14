package com.example.file.domain.gateway;

import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.ValidationError;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

// ==========================================
// 2. Excel 引擎防腐层 (Excel Engine Port)
// 隔离 EasyExcel 或 POI，领域层只认识 Map 和 JSON
// ==========================================
public interface ExcelEnginePort {
  // 读取：返回未经验证的原始数据 (每一行是一个 Map)
  List<DataRow> readExcel(InputStream in, ExcelSchema schema);

  // 写入：基于 Schema 和 数据生成 Excel (返回带有错误列的新文件流，或正常文件流)
  void writeExcel(OutputStream out, ExcelSchema schema, List<DataRow> data, List<ValidationError> errors);
}
