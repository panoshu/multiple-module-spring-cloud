package com.example.file.domain.model.schema;

// 1. 错误明细模型
public record ValidationError(
  int rowIndex,            // 出错行号
  String jsonKey,          // 出错的字段（GLOBAL表示整行级别的错误）
  String message,          // 错误信息
  Integer conflictRowIndex // [可选] 冲突的行号（用于重复校验时指明跟哪一行重复）
) {
  public ValidationError(String jsonKey, String message) {
    this(-1, jsonKey, message, null);
  }
}
