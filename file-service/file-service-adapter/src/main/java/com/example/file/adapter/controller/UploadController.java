package com.example.file.adapter.controller;

import core.application.service.DistributedDuplicateValidator;
import core.application.service.ExcelExchangeAppService;
import core.domain.model.ParseResult;
import core.domain.outbound.TemplateRepositoryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * UploadController
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/24 21:49
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class UploadController {

  private final ExcelExchangeAppService engineService;
  private final DistributedDuplicateValidator distributedValidator;
  private final TemplateRepositoryPort repository; // 用于拿 uniqueKeys

  @PostMapping("/upload-async")
  public void handleAsyncUpload(@RequestParam("file") MultipartFile file,
                                @RequestParam("templateId") String templateId,
                                @RequestParam("batchId") String batchId) throws Exception {

    // 1. 调用底层引擎，完成格式和单文件校验
    ParseResult localResult;
    try (InputStream is = file.getInputStream()) {
      localResult = engineService.parseExcel(templateId, is, file.getOriginalFilename());
    }

    // 2. 从配置获取参与防重的 Keys
    List<String> uniqueKeys = repository.loadInbound(templateId)
      .map(t -> t.inboundRule().detailZone().uniqueKeys())
      .orElse(null);

    // 3. 拦截：拿着合法的 JSON，去 Redis 进行跨时空的全局查重！
    ParseResult finalResult = distributedValidator.validateAndRecord(
      batchId, localResult, uniqueKeys, file.getOriginalFilename());

    // 4. 如果发现包含分布式校验的错误，触发生成“错题本”流程
    if (!finalResult.isSuccess()) {
      // (调用之前写好的 EasyExcelErrorExcelExporter 生成错题本并上传 OSS)
//      return Response.fail("发现错误数据，请下载错题本", generateErrorExcel(file, finalResult));
    }

    // 5. 将 finalResult.jsonPayload() 发送给后续业务逻辑 (如 MQ 削峰填谷)
//    return Response.success("文件解析且全局查重通过！");
  }
}
