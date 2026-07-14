package com.example.file.domain.gateway;

import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.ValidationError;

import java.io.InputStream;
import java.util.List;

// ==========================================
// 4. 文件存储防腐层 (File Storage Port)
// ==========================================
public interface FileStoragePort {
  String uploadJson(String fileName, String jsonContent);

  String uploadExcel(String fileName, InputStream excelStream);

  String uploadErrorExcel(InputStream originalFile, ExcelSchema schema, List<ValidationError> errors);
}
