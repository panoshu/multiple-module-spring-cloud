package com.example.file.domain.model.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// 2. 通用数据行结构体 (DataRow)
// 包含提取的原始数据、元数据（行号）以及挂载的错误列表
public record DataRow(
  int rowIndex,
  Map<String, Object> data,      // 解析出的业务数据
  List<ValidationError> errors   // 校验过程中不断追加的错误
) {
  public DataRow(int rowIndex, Map<String, Object> data) {
    this(rowIndex, data, new ArrayList<>()); // 初始化时提供一个可变 List 收集错误
  }

  public boolean hasErrors() {
    return !errors.isEmpty();
  }

  // 自动将行号注入到输出的 JSON 数据中
  public Map<String, Object> getExportableData() {
    data.put("_rowIndex", rowIndex); // 约定的系统字段
    return data;
  }
}
