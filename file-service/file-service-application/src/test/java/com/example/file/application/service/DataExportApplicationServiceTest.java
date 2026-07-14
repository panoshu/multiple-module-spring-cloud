package com.example.file.application.service;

import com.example.file.application.pipeline.ExcelWritePipeline;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("应用层: 数据导出门面服务测试")
class DataExportApplicationServiceTest {

  @Mock
  private ExcelWritePipeline writePipeline;

  @InjectMocks
  private DataExportApplicationService service;

  @Test
  @DisplayName("导出测试：正常应返回URL，异常应抛出 RuntimeException")
  void testExportDataToExcel() {
    String schemaId = "write_schema";
    Map<String, Object> discrete = Map.of("title", "测试");
    List<Map<String, Object>> table = List.of(Map.of("name", "张三"));

    // 正常场景
    when(writePipeline.processWrite(schemaId, discrete, table)).thenReturn("https://oss.com/test.xlsx");
    String url = service.exportDataToExcel(schemaId, discrete, table);
    assertEquals("https://oss.com/test.xlsx", url);

    // 异常场景
    when(writePipeline.processWrite(schemaId, discrete, table)).thenThrow(new RuntimeException("网络断开"));
    Exception ex = assertThrows(RuntimeException.class, () -> service.exportDataToExcel(schemaId, discrete, table));
    assertTrue(ex.getMessage().contains("导出 Excel 失败"));
  }
}
