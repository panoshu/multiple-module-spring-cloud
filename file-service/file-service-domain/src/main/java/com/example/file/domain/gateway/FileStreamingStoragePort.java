package com.example.file.domain.gateway;

import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.ValidationError;

import java.io.InputStream;
import java.io.OutputStream;

public interface FileStreamingStoragePort {
  OutputStream createOssOutputStream(String fileName);

  void rollback(java.util.Collection<String> fileNames); // Undo logic

  String uploadErrorExcel(InputStream originalFile, ExcelSchema schema, java.util.List<ValidationError> errors);
}
