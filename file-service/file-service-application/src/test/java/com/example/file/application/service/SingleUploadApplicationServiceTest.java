package com.example.file.application.service;

import com.example.file.application.dto.ReadResult;
import com.example.file.application.pipeline.ExcelReadPipeline;
import com.example.file.application.pipeline.ExcelStreamingPipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("应用层: 单文件上传门面服务测试")
class SingleUploadApplicationServiceTest {

  @Mock
  private ExcelStreamingPipeline streamingPipeline;
  @Mock
  private ExcelReadPipeline readPipeline;
  @Mock
  private InputStream inputStream;

  @InjectMocks
  private SingleUploadApplicationService service;

  @Test
  @DisplayName("流式处理正常路由，遇异常应返回 SystemError 结果")
  void testProcessSingleFileStream() {
    String schemaId = "test_schema";

    // 场景 1: 正常成功
    ReadResult successResult = ReadResult.success(List.of("url1"));
    when(streamingPipeline.processStream(schemaId, inputStream)).thenReturn(successResult);
    ReadResult result1 = service.processSingleFileStream(schemaId, inputStream);
    assertTrue(result1.isSuccess());

    // 场景 2: 抛出异常被兜底捕获
    when(streamingPipeline.processStream(schemaId, inputStream)).thenThrow(new RuntimeException("OOM"));
    ReadResult result2 = service.processSingleFileStream(schemaId, inputStream);
    assertFalse(result2.isSuccess());
    assertTrue(result2.globalErrors().get(0).contains("OOM"));
  }
}
