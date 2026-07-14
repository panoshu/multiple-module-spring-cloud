package com.example.file.application.pipeline;

import com.example.file.application.dto.ReadResult;
import com.example.file.domain.gateway.ExcelEnginePort;
import com.example.file.domain.gateway.FileStoragePort;
import com.example.file.domain.model.enums.BizType;
import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.ErrorFeedbackConfig;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.ValidationError;
import com.example.file.domain.repository.ExcelSchemaRepository;
import com.example.file.domain.service.DeduplicationService;
import com.example.file.domain.service.FormValidationService;
import com.example.file.domain.service.SplitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("应用层: Excel 全量读取流水线测试")
class ExcelReadPipelineTest {

  @Mock
  private ExcelSchemaRepository schemaRepository;
  @Mock
  private ExcelEnginePort excelEngine;
  @Mock
  private FormValidationService validationService;
  @Mock
  private DeduplicationService deduplicationService;
  @Mock
  private SplitService splitService;
  @Mock
  private FileStoragePort fileStorage;
  @Mock
  private InputStream fileStream;

  @InjectMocks
  private ExcelReadPipeline pipeline;

  @Test
  @DisplayName("全量成功流程：读取 -> 校验 -> 去重 -> 拆分 -> 上传 JSON")
  void testProcessSuccess() {
    ExcelSchema schema = new ExcelSchema("read_1", BizType.EMPLOYEE_ONBOARDING, null, null, null, null, null, null, null, List.of());
    when(schemaRepository.loadSchema("read_1")).thenReturn(schema);

    List<DataRow> mockRows = List.of(new DataRow(1, new HashMap<>()));
    when(excelEngine.readExcel(fileStream, schema)).thenReturn(mockRows);

    // Mock 拆分服务返回 1 个文件
    when(splitService.splitData(mockRows, schema)).thenReturn(Map.of("data.json", List.of(Map.of("a", "b"))));
    when(fileStorage.uploadJson(eq("data.json"), anyString())).thenReturn("https://oss/data.json");

    ReadResult result = pipeline.process("read_1", fileStream);

    assertTrue(result.isSuccess());
    assertEquals(1, result.ossUrls().size());

    // 验证领域的服务被依次调用
    verify(validationService, times(1)).validateRow(any(), any());
    verify(splitService, times(1)).splitData(any(), any());
  }

  @Test
  @DisplayName("全量失败流程：如果行中存在错误，应熔断上传JSON并触发错误表单回写")
  void testProcessFailure() throws Exception {
    ExcelSchema schema = new ExcelSchema("read_1", BizType.EMPLOYEE_ONBOARDING, null, null, null, null, null, null, new ErrorFeedbackConfig(true, "ERR", ""), List.of());
    when(schemaRepository.loadSchema("read_1")).thenReturn(schema);

    // 模拟返回的一行数据带有 Validation Error
    DataRow badRow = new DataRow(1, new HashMap<>(), new ArrayList<>(List.of(new ValidationError(1, "A", "Bad", null))));
    when(excelEngine.readExcel(fileStream, schema)).thenReturn(List.of(badRow));

    when(fileStorage.uploadErrorExcel(any(), any(), any())).thenReturn("https://oss/err.xlsx");

    ReadResult result = pipeline.process("read_1", fileStream);

    assertFalse(result.isSuccess());
    assertEquals("https://oss/err.xlsx", result.errorFileUrl());

    // 验证文件流被 reset 供生成器重读
    verify(fileStream).reset();

    // 验证拆分和JSON上传被完全跳过 (熔断)
    verify(splitService, never()).splitData(any(), any());
    verify(fileStorage, never()).uploadJson(anyString(), anyString());
  }
}
