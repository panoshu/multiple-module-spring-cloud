package com.example.file.application.pipeline;

import com.example.file.domain.gateway.ExcelWriteEnginePort;
import com.example.file.domain.gateway.FileStreamingStoragePort;
import com.example.file.domain.model.enums.BizType;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.repository.ExcelSchemaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("应用层: Excel 写入流水线测试")
class ExcelWritePipelineTest {

  @Mock
  private ExcelSchemaRepository schemaRepository;
  @Mock
  private ExcelWriteEnginePort writeEngine;
  @Mock
  private FileStreamingStoragePort fileStorage;

  @InjectMocks
  private ExcelWritePipeline pipeline;

  @Test
  @DisplayName("纯内存写入流程：成功创建OSS流，引擎执行写入，最后关闭流")
  void testProcessWrite() {
    ExcelSchema mockSchema = new ExcelSchema("write_1", BizType.EMPLOYEE_ONBOARDING, null, null, null, null, null, null, null, List.of());
    when(schemaRepository.loadSchema("write_1")).thenReturn(mockSchema);

    // Mock OSS 返回的流 (使用 ByteArrayOutputStream 防止真实 IO)
    ByteArrayOutputStream dummyOssStream = new ByteArrayOutputStream();
    when(fileStorage.createOssOutputStream(anyString())).thenReturn(dummyOssStream);

    String url = pipeline.processWrite("write_1", Map.of(), List.of());

    // 验证：引擎确实被调用来写入这个流了
    verify(writeEngine).writeExcel(eq(dummyOssStream), eq(mockSchema), any(), any());

    assertTrue(url.contains("EMPLOYEE_ONBOARDING_OUT_"));
  }
}
