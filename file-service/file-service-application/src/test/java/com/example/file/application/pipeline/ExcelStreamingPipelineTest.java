package com.example.file.application.pipeline;

import com.example.file.application.dto.ReadResult;
import com.example.file.domain.gateway.ExcelStreamingEnginePort;
import com.example.file.domain.gateway.FileStreamingStoragePort;
import com.example.file.domain.model.enums.BizType;
import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.ErrorFeedbackConfig;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.ValidationError;
import com.example.file.domain.repository.ExcelSchemaRepository;
import com.example.file.domain.service.FormValidationService;
import com.example.file.domain.service.StreamingDeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("应用层: Excel 流式读取流水线测试")
class ExcelStreamingPipelineTest {

  @Mock
  private ExcelSchemaRepository schemaRepo;
  @Mock
  private ExcelStreamingEnginePort excelEngine;
  @Mock
  private FormValidationService validationService;
  @Mock
  private StreamingDeduplicationService dedupService;
  @Mock
  private FileStreamingStoragePort fileStorage;
  @Mock
  private InputStream inputStream;
  @Captor
  private ArgumentCaptor<Consumer<DataRow>> rowProcessorCaptor;

  @InjectMocks
  private ExcelStreamingPipeline pipeline;

  private ExcelSchema schema;

  @BeforeEach
  void setUp() {
    schema = new ExcelSchema("read_1", BizType.EMPLOYEE_ONBOARDING, null, null, null, null, null, null, new ErrorFeedbackConfig(true, "Err", "x"), List.of());
    when(schemaRepo.loadSchema("read_1")).thenReturn(schema);
    // 防止 JsonStreamWriter 抛错，返回一个哑输出流
    lenient().when(fileStorage.createOssOutputStream(anyString())).thenReturn(new ByteArrayOutputStream());
  }

  @Test
  @DisplayName("错误回写测试：当出现校验错误时，应阻断 JSON 输出并触发上传错误表单")
  void testProcessStreamWithError() {
    // 🟢 修复：使用 doAnswer 模拟引擎实时、同步地读取出带有错误的数据，触发回调
    doAnswer(invocation -> {
      Consumer<DataRow> consumer = invocation.getArgument(2);
      DataRow badRow = new DataRow(1, new HashMap<>(), new ArrayList<>(List.of(new ValidationError(1, "A", "Error", null))));
      consumer.accept(badRow); // 实时推给 Pipeline
      return null;
    }).when(excelEngine).readExcelStream(eq(inputStream), eq(schema), any());

    // 预期该错误会触发回写机制
    when(fileStorage.uploadErrorExcel(any(), any(), any())).thenReturn("https://oss/error.xlsx");

    // 执行 pipeline
    ReadResult result = pipeline.processStream("read_1", inputStream);

    // 断言失败且返回了错误表单链接
    assertFalse(result.isSuccess());
    assertEquals("https://oss/error.xlsx", result.errorFileUrl());

    // 验证确实调用了上传错误表单的方法
    verify(fileStorage).uploadErrorExcel(any(), any(), any());
  }

  @Test
  @DisplayName("成功+回滚调度编排测试 (通过 doAnswer 模拟引擎)")
  void testPipelineFlowWithDoAnswer() {
    // 使用 doAnswer 模拟引擎实时读取出数据
    doAnswer(invocation -> {
      Consumer<DataRow> consumer = invocation.getArgument(2);
      // 发送一行正确数据
      consumer.accept(new DataRow(1, new HashMap<>(java.util.Map.of("k", "v"))));
      // 发送一行错误数据
      consumer.accept(new DataRow(2, new HashMap<>(), new ArrayList<>(List.of(new ValidationError(2, "k", "err", null)))));
      return null;
    }).when(excelEngine).readExcelStream(eq(inputStream), eq(schema), any());

    when(fileStorage.uploadErrorExcel(eq(inputStream), eq(schema), any())).thenReturn("url_err");

    ReadResult result = pipeline.processStream("read_1", inputStream);

    // 验证：发生了错误，回滚被调用
    verify(fileStorage).rollback(any());
    // 验证：触发了错误回写
    verify(fileStorage).uploadErrorExcel(eq(inputStream), eq(schema), any());
    assertFalse(result.isSuccess());
    assertEquals("url_err", result.errorFileUrl());
  }
}
