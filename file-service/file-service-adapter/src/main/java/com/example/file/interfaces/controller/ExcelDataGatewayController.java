package com.example.file.interfaces.controller;

import com.example.file.api.ExcelDataGatewayApi;
import com.example.file.api.dto.BatchReadResponse;
import com.example.file.api.dto.ExportRequest;
import com.example.file.api.dto.ReadResponse;
import com.example.file.application.dto.BatchReadResult;
import com.example.file.application.dto.ReadResult;
import com.example.file.application.service.BatchUploadApplicationService;
import com.example.file.application.service.DataExportApplicationService;
import com.example.file.application.service.SingleUploadApplicationService;
import com.example.file.interfaces.mapper.ExcelGatewayMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ExcelDataGatewayController implements ExcelDataGatewayApi {

  private final SingleUploadApplicationService singleUploadService;
  private final BatchUploadApplicationService batchUploadService;
  private final DataExportApplicationService exportService;

  // 注入 MapStruct 自动生成的 Mapper 实现
  private final ExcelGatewayMapper mapper;

  @Override
  public ReadResponse uploadSingleFile(String schemaId, MultipartFile file) {
    log.info("API请求: 单文件上传, 文件名: {}", file.getOriginalFilename());
    try (InputStream in = file.getInputStream()) {

      // 1. 调用应用层
      ReadResult appResult = (file.getSize() > 5 * 1024 * 1024)
        ? singleUploadService.processSingleFileStream(schemaId, in)
        : singleUploadService.processSingleFileBatch(schemaId, in);

      // 2. 优雅返回
      return mapper.toReadResponse(appResult);

    } catch (IOException e) {
      log.error("获取文件流失败", e);
      return new ReadResponse(false, List.of(), null, List.of("获取上传文件失败: " + e.getMessage()));
    }
  }

  @Override
  public BatchReadResponse uploadBatchZip(String taskId, String schemaId, MultipartFile file) {
    log.info("API请求: ZIP 批量上传, 任务ID: {}", taskId);
    try (InputStream in = file.getInputStream()) {

      // 1. 调用应用层
      BatchReadResult appResult = batchUploadService.processZipStream(taskId, schemaId, in);

      // 2. 优雅返回 (嵌套 List 的转换全自动完成)
      return mapper.toBatchReadResponse(appResult);

    } catch (IOException e) {
      log.error("获取 ZIP 文件流失败", e);
      throw new RuntimeException("处理压缩包失败");
    }
  }

  @Override
  public String exportData(ExportRequest request) {
    log.info("API请求: 数据导出, schemaId: {}", request.schemaId());
    return exportService.exportDataToExcel(
      request.schemaId(),
      request.discreteData(),
      request.tableData()
    );
  }
}
