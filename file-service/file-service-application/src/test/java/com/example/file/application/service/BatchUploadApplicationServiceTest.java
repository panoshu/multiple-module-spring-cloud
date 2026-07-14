package com.example.file.application.service;

import com.example.file.application.dto.BatchReadResult;
import com.example.file.application.dto.ReadResult;
import com.example.file.application.pipeline.ExcelStreamingPipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("应用层: ZIP 批量解压与派发服务测试")
class BatchUploadApplicationServiceTest {

  @Mock
  private ExcelStreamingPipeline pipeline;

  @InjectMocks
  private BatchUploadApplicationService service;

  @Test
  @DisplayName("ZIP解压派发测试：应跳过非 .xlsx 文件，且成功调用 Pipeline")
  void testProcessZipStream() throws Exception {
    // 1. 在内存中创建一个包含 2 个文件的 ZIP 压缩包 (1个excel, 1个txt)
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      zos.putNextEntry(new ZipEntry("data.xlsx"));
      zos.write("dummy excel data".getBytes());
      zos.closeEntry();

      zos.putNextEntry(new ZipEntry("readme.txt"));
      zos.write("ignore me".getBytes());
      zos.closeEntry();
    }
    ByteArrayInputStream zipIn = new ByteArrayInputStream(baos.toByteArray());

    // 2. Mock Pipeline 行为
    when(pipeline.processStream(eq("schema_1"), any())).thenReturn(ReadResult.success(List.of("url1")));

    // 3. 执行
    BatchReadResult result = service.processZipStream("task_001", "schema_1", zipIn);

    // 4. 验证
    assertEquals("task_001", result.batchTaskId());
    assertEquals(1, result.fileSummaries().size(), "应该只处理了 .xlsx 文件");
    assertEquals("data.xlsx", result.fileSummaries().get(0).fileName());
    assertTrue(result.fileSummaries().get(0).success());

    // 验证 Pipeline 真的只被调用了一次
    verify(pipeline, times(1)).processStream(eq("schema_1"), any());
  }
}
