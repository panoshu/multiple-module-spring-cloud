package com.example.file.infrastructure.excel;

import com.example.file.domain.gateway.FileStoragePort;
import com.example.file.domain.gateway.FileStreamingStoragePort;
import com.example.file.domain.model.region.HorizontalTableRegionConfig;
import com.example.file.domain.model.schema.ExcelSchema;
import com.example.file.domain.model.schema.ValidationError;
import org.apache.fesod.sheet.FesodSheet;

import java.io.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * OssFileStorageAdapter
 * 统一承接全量文件存储与流式 OSS 分片直传
 */
public class OssFileStorageAdapter implements FileStoragePort, FileStreamingStoragePort {

  // ==========================================
  // 1. 流式直传实现 (FileStreamingStoragePort)
  // ==========================================
  @Override
  public OutputStream createOssOutputStream(String fileName) {
    // 生产环境中，这里应该返回阿里云 OSS 的 MultipartUploadOutputStream。
    // 该流会在每次写入满 5MB 时自动向 OSS 提交一个分片。
    // 此处为了代码可运行，使用本地临时目录模拟。
    try {
      File dir = new File("/tmp/oss_staging/");
      if (!dir.exists()) {
        dir.mkdirs();
      }
      return new FileOutputStream(new File(dir, fileName));
    } catch (FileNotFoundException e) {
      throw new RuntimeException("无法创建底层流式存储管道", e);
    }
  }

  @Override
  public void rollback(Collection<String> fileNames) {
    // 当流水线中途抛出异常时，回滚/删除云端上传了一半的残缺文件
    fileNames.forEach(name -> {
      File file = new File("/tmp/oss_staging/" + name);
      if (file.exists()) {
        file.delete();
      }
    });
  }

  // ==========================================
  // 2. 错误回写与普通上传 (FileStoragePort & FileStreamingStoragePort)
  // ==========================================
  @Override
  public String uploadErrorExcel(InputStream originalFileStream, ExcelSchema schema, List<ValidationError> errors) {
    Map<Integer, String> rowErrorMap = errors.stream()
      .collect(Collectors.groupingBy(
        ValidationError::rowIndex,
        Collectors.mapping(ValidationError::message, Collectors.joining(" | "))
      ));

    // 动态获取第一个横表的别名表头行，作为错误信息列头的位置
    int headerRowIndex = schema.regions().stream()
      .filter(r -> r instanceof HorizontalTableRegionConfig)
      .map(r -> ((HorizontalTableRegionConfig) r).tableMeta().nameRowIndex() - 1)
      .findFirst()
      .orElse(0);

    String errorFileName = "error_" + System.currentTimeMillis() + schema.errorFeedback().outputSuffix();
    File tempErrorFile = new File("/tmp/" + errorFileName);

    // 使用 Apache Fesod 注入配置驱动的拦截器
    FesodSheet.write(tempErrorFile)
      .withTemplate(originalFileStream) // 基于用户上传的原文件模板
      .sheet()
      .registerWriteHandler(new ErrorColumnAppendHandler(rowErrorMap, schema.errorFeedback(), headerRowIndex))
      .doWrite(List.of());

    // 真实场景：将 tempErrorFile 上传至 OSS 后删除本地文件
    return "https://oss.yourcompany.com/errors/" + errorFileName;
  }

  @Override
  public String uploadJson(String fileName, String jsonContent) {
    // 普通 JSON 字符串全量上传
    return "https://oss.yourcompany.com/json/" + fileName;
  }

  @Override
  public String uploadExcel(String fileName, InputStream excelStream) {
    // 普通 Excel 流全量上传
    return "https://oss.yourcompany.com/excel/" + fileName;
  }
}
