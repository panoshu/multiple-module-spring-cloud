package com.example.consumer.application.pdf;

import com.example.consumer.application.pdf.model.BankLetterContext;
import com.example.consumer.application.pdf.model.GovNoticeContext;
import com.example.consumer.application.pdf.model.MonthReportContext;
import com.example.share.logging.annotation.WithDurationLogging;
import com.example.shared.pdf.model.PdfMetadata;
import com.example.shared.pdf.service.PdfService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.List;
import java.util.stream.IntStream;

/**
 * PdfApplicationService
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/1/24 14:36
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAppServiceImpl implements IDocumentAppService {

  private final PdfService pdfService;
  // 假设这是你的业务 Repository
  // private final NoticeRepository noticeRepository;
  // private final CustomerRepository customerRepository;
  // private final TransactionRepository transactionRepository;

  @Override
  public byte[] generateGovNotice(Long noticeId) {
    // 1. 查询业务数据 (模拟)
    // NoticeEntity notice = noticeRepository.findById(noticeId);

    // 2. 组装 Context (红头文件)
    GovNoticeContext ctx = GovNoticeContext.builder()
      // 业务字段
      .orgName("信息技术部 - " + noticeId.toString())
      .docNo("信发[2026] 101号")
      .mainTitle("关于春节期间系统封网的通知")
      .bodyHtml("<p>各部门：</p><p>为保障春节期间系统稳定，定于<strong>1月28日</strong>起进行封网。</p>")
      .metadata(PdfMetadata.builder()
        .title("红头公文-信发[2026] 101号")
        .author("IT Dept")
        .keywords("封网, 通知, 2026")
        .subject("subject")
        .build())
      .build();

    // 4. 调用组件生成
    return pdfService.generatePdf(ctx);
  }

  @Override
  @WithDurationLogging
  public byte[] generateRiskLetter(Long customerId) {
    // 1. 查询客户及评估数据
    // CustomerEntity cust = customerRepository.findById(customerId);
    String customerName = "张三";

    BankLetterContext ctx = BankLetterContext.builder()
      .customerName(customerName)
      .accountNo("6222****8888")
      .riskLevel("R3-稳健型")
      .metadata(PdfMetadata.builder()
        .title("风险评估结果告知书")
        .subject("客户: " + customerName + customerId.toString())
        .build())
      .build();

    return pdfService.generatePdf(ctx);
  }

  @Override
  public byte[] generateMonthReport(String month) {
    // 1. 查询海量数据
    // List<TransEntity> transList = transactionRepository.findByMonth(month);

    // 2. 组装 Context (报表)
    List<MonthReportContext.ReportItem> reportItems = IntStream.rangeClosed(1, 10)
      .mapToObj(i -> MonthReportContext.ReportItem.builder()
        .date(month + "-" + String.format("%02d", i))
        .category("在线支付")
        .amount("199.00")
        .remark("订单号: " + System.currentTimeMillis())
        .build())
      .toList();

    // 2. 构建 Context
    // 利用了 @Singular，可以直接传 List，或者用 clearItems().item(...)
    MonthReportContext ctx = MonthReportContext.builder()
      .reportTitle(month + " 月度交易流水明细")
      .items(reportItems) // @Singular 自动生成的复数形式方法
      .metadata(PdfMetadata.builder()
        .title("月度报表-" + month)
        .build())
      .build();

    //    FileMetaResp meta = fileTemplate.upload(pdfBytes, "月度报表-" + month, "RECEIPT");

    //    log.info("Large report generated: fileId={}, size={}", meta.getFileId(), meta.getSize());
    //    return meta.getFileId(); // 返回 ID 给前端，前端再去文件服务下载

    return pdfService.generatePdf(ctx);
  }

  @Override
  @WithDurationLogging
  public void generateReportStream(String month, OutputStream outputStream) {
    // 1. 查询海量数据
    // List<TransEntity> transList = transactionRepository.findByMonth(month);

    // 2. 组装 Context (报表)
    // 假设我们要一个个添加 item (模拟处理过程)
    var ctxBuilder = MonthReportContext.builder()
      .reportTitle(month + " 月度交易流水明细")
      .metadata(PdfMetadata.builder()
        .title("月度报表-" + month)
        .build());

    // 模拟循环添加 (使用 @Singular 生成的单数方法 item)
    for (int i = 1; i <= 20; i++) {
      ctxBuilder.item(MonthReportContext.ReportItem.builder()
        .date(month + "-" + String.format("%02d", i))
        .category("在线支付")
        .amount("8888.00")
        .remark("订单号: " + System.currentTimeMillis())
        .build());
    }

    //    FileMetaResp meta = fileTemplate.upload(fileName, "MONTH_REPORT", outputStream -> {
    //      // 这一步在 Virtual Thread 中执行
    //      pdfService.generatePdfStream(ctx, outputStream);
    //    });
    //    log.info("Large report generated: fileId={}, size={}", meta.getFileId(), meta.getSize());
    //    return meta.getFileId(); // 返回 ID 给前端，前端再去文件服务下载

    pdfService.generatePdfStream(ctxBuilder.build(), outputStream);
  }
}
