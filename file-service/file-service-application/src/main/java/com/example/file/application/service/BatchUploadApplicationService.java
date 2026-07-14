package com.example.file.application.service;

import com.example.file.application.dto.BatchReadResult;
import com.example.file.application.dto.FileProcessSummary;
import com.example.file.application.dto.ReadResult;
import com.example.file.application.pipeline.ExcelStreamingPipeline;
import com.example.file.infrastructure.excel.io.CloseShieldInputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * BatchUploadApplicationService
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/26 21:58
 */
@Slf4j
@RequiredArgsConstructor
public class BatchUploadApplicationService {
  private final ExcelStreamingPipeline pipeline;

  public BatchReadResult processZipStream(String taskId, String schemaId, InputStream rawZip) {
    var summaries = new ArrayList<FileProcessSummary>();
    try (ZipInputStream zis = new ZipInputStream(rawZip)) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        if (entry.isDirectory() || !entry.getName().endsWith(".xlsx")) {
          zis.closeEntry();
          continue;
        }

        // Protect the ZIP stream from being closed by the Excel parser
        InputStream safeStream = new CloseShieldInputStream(zis);

        try {
          ReadResult result = pipeline.processStream(schemaId, safeStream);
          summaries.add(new FileProcessSummary(
            entry.getName(), result.isSuccess(), result.ossUrls(), result.errorFileUrl()
          ));
        } finally {
          zis.closeEntry();
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to process ZIP", e);
    }
    return new BatchReadResult(taskId, summaries);
  }
}
