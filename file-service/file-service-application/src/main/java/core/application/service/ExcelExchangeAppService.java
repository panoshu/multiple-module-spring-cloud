package core.application.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.exception.ExcelAnalysisException;
import core.domain.exception.HeaderValidationException;
import core.domain.model.*;
import core.domain.outbound.OutboundAdapterPort;
import core.domain.outbound.SchemaValidatorPort;
import core.domain.outbound.TemplateRepositoryPort;
import infrastructure.excel.EngineReadListener;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 核心引擎门面服务 (应用层编排)
 */
@Service
@RequiredArgsConstructor
public class ExcelExchangeAppService {

  private final TemplateRepositoryPort templateRepository;
  private final SchemaValidatorPort schemaValidator;
  private final List<OutboundAdapterPort> outboundAdapters;

  /**
   * Inbound: 解析 Excel 并输出校验后的 JSON
   */
  public ParseResult parseExcel(String inboundTemplateId, InputStream excelStream, String fileName) {
    // 1. 获取模板 (内置本地 Cache 机制)
    InboundTemplate template = templateRepository.loadInbound(inboundTemplateId)
      .orElseThrow(() -> new IllegalArgumentException("未找到输入配置: " + inboundTemplateId));

    EngineReadListener listener = new EngineReadListener(template, schemaValidator, fileName);

    try {
      // 3. 启动 EasyExcel 流式读取
      EasyExcel.read(excelStream, listener)
        .headRowNumber(0)
        .sheet()
        .doRead();

      // 4. 组装结果 (判断是完美成功还是部分成功)
      var errors = listener.getDetailErrors();
      var status = errors.isEmpty() ? ParseStatus.SUCCESS : ParseStatus.PARTIAL_SUCCESS;
      var jsonPayload = listener.getParsedDataTree().toString();

      return new ParseResult(jsonPayload, status, errors);

    } catch (ExcelAnalysisException e) {
      // 5. 捕获 Fail-Fast 的表头异常，封装为失败的 ParseResult
      if (e.getCause() instanceof HeaderValidationException headerEx) {
        return new ParseResult(null, ParseStatus.FAILED, headerEx.getHeaderErrors());
      }
      throw new RuntimeException("Excel 解析发生未知内部异常", e);
    }
  }

  /**
   * 批处理解析多份文件，并执行跨文件防重校验
   */
  public Map<String, ParseResult> parseBatch(List<BatchFileRequest> requests) {
    Map<String, ParseResult> resultMap = new HashMap<>();
    List<String> globalUniqueKeys = null;

    // 1. 逐个文件单体解析
    for (BatchFileRequest req : requests) {
      InboundTemplate inboundTemplate = templateRepository.loadInbound(req.templateId())
        .orElseThrow(() -> new RuntimeException("找不到输入模板: " + req.templateId()));

      // 随便取一个模板的 uniqueKeys 作为全局校验规则（这里假设同一个批次的业务主键规则是一致的）
      if (globalUniqueKeys == null && inboundTemplate.inboundRule().detailZone().uniqueKeys() != null) {
        globalUniqueKeys = inboundTemplate.inboundRule().detailZone().uniqueKeys();
      }

      EngineReadListener listener = new EngineReadListener(inboundTemplate, schemaValidator, req.fileName());

      try {
        EasyExcel.read(req.stream(), listener).headRowNumber(0).sheet().doRead();
        ParseResult result = new ParseResult(
          listener.getParsedDataTree().toString(),
          listener.getDetailErrors().isEmpty() ? ParseStatus.SUCCESS : ParseStatus.PARTIAL_SUCCESS,
          listener.getDetailErrors()
        );
        resultMap.put(req.fileName(), result);
      } catch (ExcelAnalysisException e) {
        if (e.getCause() instanceof HeaderValidationException headerEx) {
          resultMap.put(req.fileName(), new ParseResult(null, ParseStatus.FAILED, headerEx.getHeaderErrors()));
        } else {
          throw e;
        }
      }
    }

    // 2. 执行跨文件全局校验！(黑魔法在这里)
    CrossFileValidator.validateDuplicates(resultMap, globalUniqueKeys);

    return resultMap;
  }

  /**
   * 导出 Excel (现在根据 InboundTemplate 寻找关联的 OutboundTemplate)
   */
  public void exportExcel(String inboundTemplateId, String jsonPayload, OutputStream outputStream) {
    InboundTemplate inboundTemplate = templateRepository.loadInbound(inboundTemplateId)
      .orElseThrow(() -> new IllegalArgumentException("未找到输入配置: " + inboundTemplateId));

    OutboundTemplate outboundTemplate = templateRepository.loadOutbound(inboundTemplate.outboundTemplateId())
      .orElseThrow(() -> new IllegalArgumentException("未找到标准输出配置"));

    // 1. 获取配置要求的引擎类型
    ExportEngineType targetEngine = outboundTemplate.outboundRule().getSafeEngineType();

    // 2. 策略模式：在注册的适配器中寻找支持该类型的实现
    OutboundAdapterPort targetAdapter = outboundAdapters.stream()
      .filter(adapter -> adapter.supports(targetEngine))
      .findFirst()
      .orElseThrow(() -> new UnsupportedOperationException("系统未注册支持 [" + targetEngine + "] 类型的输出引擎！"));

    // 3. 委派执行
    targetAdapter.write(outboundTemplate, jsonPayload, outputStream);
  }


  // 辅助请求对象
  public record BatchFileRequest(String fileName, String templateId, InputStream stream) {
  }

}
