package com.example.file.api;

import com.example.file.api.dto.BatchReadResponse;
import com.example.file.api.dto.ExportRequest;
import com.example.file.api.dto.ReadResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/api/v1/gateway/excel")
public interface ExcelDataGatewayApi {

  @PostExchange(value = "/upload/single", contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
  ReadResponse uploadSingleFile(
    @RequestParam("schemaId") String schemaId,
    @RequestPart("file") MultipartFile file
  );

  @PostExchange(value = "/upload/batch", contentType = MediaType.MULTIPART_FORM_DATA_VALUE)
  BatchReadResponse uploadBatchZip(
    @RequestParam("taskId") String taskId,
    @RequestParam("schemaId") String schemaId,
    @RequestPart("file") MultipartFile file
  );

  @PostExchange(value = "/export", contentType = MediaType.APPLICATION_JSON_VALUE)
  String exportData(@RequestBody ExportRequest request);
}
