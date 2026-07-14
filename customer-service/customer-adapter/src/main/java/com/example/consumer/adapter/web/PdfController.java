package com.example.consumer.adapter.web;

import com.example.consumer.application.pdf.IDocumentAppService;
import com.example.share.logging.annotation.WithDurationLogging;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * PdfController
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/22 22:29
 */
@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class PdfController {

  private final IDocumentAppService documentAppService;

  /**
   * 访问地址: <a href="http://localhost:18090/api/docs/notice/1">...</a>
   */
  @WithDurationLogging
  @GetMapping("/notice/{id}")
  public ResponseEntity<byte[]> downloadNotice(@PathVariable("id") Long id) {

    byte[] pdfBytes = documentAppService.generateGovNotice(id);

    String fileName = "公文_" + id + ".pdf";
    String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

    return ResponseEntity.ok()
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
      .contentType(MediaType.APPLICATION_PDF)
      .body(pdfBytes);
  }

  /**
   * 访问地址: <a href="http://localhost:18090/api/docs/letter/200">...</a>
   */
  @GetMapping("/letter/{custId}")
  public ResponseEntity<byte[]> previewLetter(@PathVariable("custId") Long custId) {
    byte[] pdfBytes = documentAppService.generateRiskLetter(custId);
    // 使用 inline 支持浏览器直接打开
    return ResponseEntity.ok()
      .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + "risk_letter_" + custId + ".pdf")
      .contentType(MediaType.APPLICATION_PDF)
      .body(pdfBytes);
  }

  /**
   * 访问地址: <a href="http://localhost:18090/api/docs/report?month">...</a>
   */
  @GetMapping("/report")
  public ResponseEntity<byte[]> downloadReport(@RequestParam(name = "month", defaultValue = "202512") String month) {
    byte[] pdfBytes = documentAppService.generateMonthReport(month);
    return ResponseEntity.ok()
      .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + "report_" + month + ".pdf")
      .contentType(MediaType.APPLICATION_PDF)
      .body(pdfBytes);
  }

  /**
   * 访问地址: <a href="http://localhost:18090/api/docs/report/stream?month">...</a>
   */
  @GetMapping("/report/stream")
  public void downloadReportStream(@RequestParam(name = "month", defaultValue = "202601") String month, HttpServletResponse response) throws IOException {
    // 设置响应头 (告诉浏览器这是个文件)
    String fileName = URLEncoder.encode("月度报表_" + month + ".pdf", StandardCharsets.UTF_8).replace("+", "%20");
    response.setContentType("application/pdf");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName);


    // 调用流式方法，直接写入 Response 的 OutputStream
    // 此时 PDF 生成器产生的数据会直接流向客户端，服务器端不需要申请巨大的 byte[] 数组
    documentAppService.generateReportStream(month, response.getOutputStream());
  }
}
