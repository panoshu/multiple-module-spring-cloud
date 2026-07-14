package com.example.file.application.pipeline;

import com.example.file.application.dto.ReadResult;
import com.example.file.domain.gateway.ExcelEnginePort;
import com.example.file.domain.gateway.FileStoragePort;
import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.FieldConfig;
import com.example.file.domain.model.schema.ValidationError;
import com.example.file.domain.repository.ExcelSchemaRepository;
import com.example.file.domain.service.DeduplicationService;
import com.example.file.domain.service.FormValidationService;
import com.example.file.domain.service.SplitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 全量读取流水线 (In-Memory Batch Pipeline)
 * 适用于中小型 Excel 文件，直接将所有数据加载到内存中进行校验、去重和拆分。
 */
@Slf4j
@RequiredArgsConstructor
public class ExcelReadPipeline {

  private final ExcelSchemaRepository schemaRepository;
  private final ExcelEnginePort excelEngine;
  private final FormValidationService validationService;
  private final DeduplicationService deduplicationService;
  private final SplitService splitService;
  private final FileStoragePort fileStorage;

  /**
   * 核心编排流程
   * 注意：由于 InputStream 只能读取一次，如果后续出错需要生成错误反馈文件，
   * 外层调用此方法时，建议传入支持 mark/reset 的流或 ByteArrayInputStream。
   */
  public ReadResult process(String schemaId, InputStream fileStream) {
    // 1. 加载配置 Schema
    ExcelSchema schema = schemaRepository.loadSchema(schemaId);
    List<FieldConfig> dataFields = schema.getAllDataFields();

    // 2. 物理读取：将 Excel 转换为内存中的领域模型 List<DataRow>
    // 假设底层引擎已经处理好行号映射 (rowIndex)
    List<DataRow> rows = excelEngine.readExcel(fileStream, schema);

    // 3. 结构与规则校验 (遍历内存集合)
    for (DataRow row : rows) {
      validationService.validateRow(row, dataFields);
    }

    // 4. 防重校验 (基于 List 的全量去重)
    if (schema.deduplication() != null && schema.deduplication().enabled()) {
      deduplicationService.processDeduplication(rows, schema.deduplication());
    }

    // 5. 统一收集并判断错误
    List<ValidationError> allErrors = rows.stream()
      .filter(DataRow::hasErrors)
      .flatMap(row -> row.errors().stream())
      .toList();

    if (!allErrors.isEmpty()) {
      // 触发错误回写策略
      if (schema.errorFeedback() != null && schema.errorFeedback().enabled()) {
        try {
          fileStream.reset(); // 重置流指针，以便错误生成器重新读取原文件
          String errorFileUrl = fileStorage.uploadErrorExcel(fileStream, schema, allErrors);
          return ReadResult.failure(errorFileUrl);
        } catch (Exception e) {
          throw new RuntimeException("生成错误反馈文件失败，可能流不支持重置", e);
        }
      }
      // 如果未开启错误回写，直接抛出系统级错误摘要
      return ReadResult.systemError(allErrors.stream().map(ValidationError::message).toList());
    }

    // 6. 内存数据拆分 (将一个大的 List 拆成多个基于规则命名的 Map 集合)
    Map<String, List<Map<String, Object>>> splitDataFiles = splitService.splitData(rows, schema);

    // 7. 序列化并上传 JSON 到 OSS
    List<String> uploadedOssUrls = splitDataFiles.entrySet().stream()
      .map(entry -> {
        String fileName = entry.getKey();
        String jsonContent = toJsonString(entry.getValue()); // 底层调用 Jackson 或 Gson
        return fileStorage.uploadJson(fileName, jsonContent);
      })
      .toList();

    return ReadResult.success(uploadedOssUrls);
  }

  // 内部 JSON 序列化工具桥接 (可提至基础工具类)
  private String toJsonString(Object obj) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
    } catch (Exception e) {
      throw new RuntimeException("JSON 序列化失败", e);
    }
  }
}
