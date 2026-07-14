package com.example.file.application.pipeline;

import com.example.file.domain.gateway.ExcelWriteEnginePort;
import com.example.file.domain.gateway.FileStreamingStoragePort;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.repository.ExcelSchemaRepository;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;

/**
 * 纯内存流式写入流水线 (Pure Streaming Write Pipeline)
 * 践行零磁盘IO原则，直接将 Fesod 渲染出的字节流实时通过网络推送到云端存储。
 */
public class ExcelWritePipeline {

  private final ExcelSchemaRepository schemaRepository;
  private final ExcelWriteEnginePort writeEngine;
  private final FileStreamingStoragePort fileStreamingStoragePort; // 🟢 切换为流式存储端口

  public ExcelWritePipeline(
    ExcelSchemaRepository schemaRepository,
    ExcelWriteEnginePort writeEngine,
    FileStreamingStoragePort fileStreamingStoragePort) {
    this.schemaRepository = schemaRepository;
    this.writeEngine = writeEngine;
    this.fileStreamingStoragePort = fileStreamingStoragePort;
  }

  /**
   * 接收结构化数据，纯内存流式渲染并直传云端，生成可下载的 Excel 链接
   *
   * @param schemaId     写入配置ID
   * @param discreteData 离散主体区数据 JSON Map
   * @param tableData    横表明细表数据 JSON List
   * @return 最终在云端生成的 Excel 下载 URL
   */
  public String processWrite(String schemaId, Map<String, Object> discreteData, List<Map<String, Object>> tableData) {
    // 1. 加载配置 Schema
    ExcelSchema schema = schemaRepository.loadSchema(schemaId);

    // 2. 根据业务动态生成最终的文件名
    String fileName = schema.bizType().name() + "_OUT_" + System.currentTimeMillis() + ".xlsx";

    // 3. 🟢 核心流式对冲：直接获取直连云端 OSS 的分片上传输出流
    // 全程在堆内存流转，不触碰任何本地容器的磁盘文件系统，完美规避磁盘IO瓶颈和 OOM
    try (OutputStream out = fileStreamingStoragePort.createOssOutputStream(fileName)) {

      // 4. 召唤 Apache Fesod 引擎执行物理写入
      // Fesod 会在自绘完成离散区和明细行时，实时执行 out.write() 顺着网络刷向云端
      writeEngine.writeExcel(out, schema, discreteData, tableData);

      // 5. 顺着 try-with-resources 退出，out.close() 会被自动调用
      // 这将隐式触发云端的“分片上传完成”(Complete Multipart Upload) 指令，文件在云端正式合拢落地。

    } catch (Exception e) {
      // 如果中间任何一个环节崩溃，可以在此呼叫 fileStreamingStoragePort.rollback() 擦除上传了一半的残页
      throw new RuntimeException("纯内存流式渲染并直传 Excel 失败", e);
    }

    // 6. 返回约定的云端文件下载地址（也可以由 fileStreamingStoragePort 提供标准解析方法）
    return getStandardDownloadUrl(fileName);
  }

  private String getStandardDownloadUrl(String fileName) {
    return "https://oss.yourcompany.com/excel/" + fileName;
  }
}
