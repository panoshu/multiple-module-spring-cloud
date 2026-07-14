package com.example.file.application.service;

import com.example.file.application.dto.ReadResult;
import com.example.file.application.pipeline.ExcelReadPipeline;
import com.example.file.application.pipeline.ExcelStreamingPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;

/**
 * SingleUploadApplicationService
 * 单文件上传应用服务（Facade 门面）
 * 负责接收外部（Controller / MQ）的单文件处理请求，并路由给具体的流水线。
 */
@Slf4j
@RequiredArgsConstructor
public class SingleUploadApplicationService {

  private final ExcelStreamingPipeline streamingPipeline;
  private final ExcelReadPipeline readPipeline;

  /**
   * 流式处理单文件（推荐：防 OOM，适合超大文件）
   *
   * @param schemaId    业务配置的 Schema ID
   * @param inputStream 上传的单个 Excel 文件流
   * @return 统一的读取处理结果 DTO
   */
  public ReadResult processSingleFileStream(String schemaId, InputStream inputStream) {
    log.info("接收到单文件流式处理任务, schemaId: {}", schemaId);
    try {
      return streamingPipeline.processStream(schemaId, inputStream);
    } catch (Exception e) {
      log.error("单文件流式处理任务失败, schemaId: {}", schemaId, e);
      return ReadResult.systemError(java.util.List.of("系统处理异常: " + e.getMessage()));
    }
  }

  /**
   * 全量内存处理单文件（适合几千行以内的小文件，或者需要复杂全局跨行运算的场景）
   *
   * @param schemaId    业务配置的 Schema ID
   * @param inputStream 上传的单个 Excel 文件流
   * @return 统一的读取处理结果 DTO
   */
  public ReadResult processSingleFileBatch(String schemaId, InputStream inputStream) {
    log.info("接收到单文件全量批处理任务, schemaId: {}", schemaId);
    try {
      return readPipeline.process(schemaId, inputStream);
    } catch (Exception e) {
      log.error("单文件全量批处理任务失败, schemaId: {}", schemaId, e);
      return ReadResult.systemError(java.util.List.of("系统处理异常: " + e.getMessage()));
    }
  }
}
