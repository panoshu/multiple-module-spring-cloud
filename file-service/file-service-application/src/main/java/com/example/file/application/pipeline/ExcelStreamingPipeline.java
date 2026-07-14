package com.example.file.application.pipeline;

import com.example.file.application.dto.ReadResult;
import com.example.file.domain.gateway.ExcelStreamingEnginePort;
import com.example.file.domain.gateway.FileStreamingStoragePort;
import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.FieldConfig;
import com.example.file.domain.model.schema.ValidationError;
import com.example.file.domain.repository.ExcelSchemaRepository;
import com.example.file.domain.service.FormValidationService;
import com.example.file.domain.service.StreamingDeduplicationService;
import com.example.file.infrastructure.excel.io.JsonStreamWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class ExcelStreamingPipeline {
  private final ExcelSchemaRepository schemaRepo;
  private final ExcelStreamingEnginePort excelEngine;
  private final FormValidationService validationService;
  private final StreamingDeduplicationService dedupService;
  private final FileStreamingStoragePort fileStorage;

  public ReadResult processStream(String schemaId, InputStream stream) {
    ExcelSchema schema = schemaRepo.loadSchema(schemaId);
    List<FieldConfig> dataFields = schema.getAllDataFields();
    Map<String, JsonStreamWriter> jsonWriters = new HashMap<>();
    List<ValidationError> globalErrors = new ArrayList<>();
    boolean[] hasError = {false}; // array to allow modification inside lambda

    try {
      excelEngine.readExcelStream(stream, schema, row -> {
        validationService.validateRow(row, dataFields);
        if (schema.deduplication() != null) {
          dedupService.processDeduplication(row, schema.deduplication());
        }

        if (row.hasErrors()) {
          hasError[0] = true;
          globalErrors.addAll(row.errors());
          return; // Skip writing to JSON
        }

        if (!hasError[0]) {
          String splitName = buildSplitName(row, schema);
          JsonStreamWriter writer = jsonWriters.computeIfAbsent(splitName,
            k -> new JsonStreamWriter(fileStorage.createOssOutputStream(k))
          );
          writer.write(row.getExportableData());
        }
      });
    } finally {
      jsonWriters.values().forEach(JsonStreamWriter::close);
    }

    if (hasError[0]) {
      fileStorage.rollback(jsonWriters.keySet());
      // In reality, you'd reset the InputStream or pass a cached copy to uploadErrorExcel
      String errorUrl = fileStorage.uploadErrorExcel(stream, schema, globalErrors);
      return ReadResult.failure(errorUrl);
    }

    return ReadResult.success(jsonWriters.keySet());
  }

  private String buildSplitName(DataRow row, ExcelSchema schema) {
    if (schema.splitStrategy() == null || !schema.splitStrategy().enabled()) {
      return schema.bizType().name() + "_all.json";
    }
    String val = schema.splitStrategy().splitBy().stream()
      .map(k -> String.valueOf(row.data().getOrDefault(k, "UNKNOWN"))).reduce("", (a, b) -> a + "_" + b);
    return schema.splitStrategy().outputNaming()
      .replace("${bizType}", schema.bizType().name()).replace("${splitValue}", val);
  }
}
