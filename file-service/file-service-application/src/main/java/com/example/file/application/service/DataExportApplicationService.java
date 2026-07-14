package com.example.file.application.service;

import com.example.file.application.pipeline.ExcelWritePipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * DataExportApplicationService
 * 数据导出应用服务（Facade 门面）
 * 负责将来自外部系统的结构化 JSON 数据，转换为 Excel 表单并上传云端。
 */
@Slf4j
@RequiredArgsConstructor
public class DataExportApplicationService {

  private final ExcelWritePipeline writePipeline;

  /**
   * 接收结构化数据并导出为 Excel
   *
   * @param schemaId     对应的写入模板/白板 Schema ID
   * @param discreteData 主体/离散区数据 (通常是单条记录，如企业信息)
   * @param tableData    明细/横表区数据 (List 集合，如员工列表)
   * @return 云端 OSS 下载链接
   */
  public String exportDataToExcel(String schemaId, Map<String, Object> discreteData, List<Map<String, Object>> tableData) {
    log.info("接收到数据导出渲染任务, 目标 schemaId: {}", schemaId);

    try {
      // 直接调用写入流水线，实现纯内存无盘化的流式直传
      String downloadUrl = writePipeline.processWrite(schemaId, discreteData, tableData);
      log.info("数据导出渲染任务完成, 下载地址: {}", downloadUrl);
      return downloadUrl;
    } catch (Exception e) {
      log.error("数据导出渲染任务失败, schemaId: {}", schemaId, e);
      throw new RuntimeException("导出 Excel 失败，请稍后重试", e);
    }
  }
}
