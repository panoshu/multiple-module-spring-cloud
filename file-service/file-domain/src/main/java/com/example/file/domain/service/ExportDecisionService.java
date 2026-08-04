package com.example.file.domain.service;

import com.example.file.domain.gateway.ExcelExporter;
import com.example.file.domain.model.valueobject.SplitUnit;
import com.example.file.domain.model.valueobject.ValidationResult;
import com.example.shared.domain.annotation.DomainService;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 导出决策领域服务.
 *
 * <p>封装"校验通过才导出"的业务决策逻辑。当 ValidationResult 无效时静默跳过导出，
 * 当有效时委托给 ExcelExporter SPI 执行实际填充。
 *
 * <p>设计说明：
 * <ul>
 *   <li>无状态（{@code @DomainService}），符合 03-领域模型约束.md §六"领域服务必须无状态"</li>
 *   <li>不持有 ExcelExporter 引用，由调用方在方法参数中传入（与 {@link DataValidator}
 *       接收 {@link com.example.file.domain.gateway.ExpressionEvaluator} 的模式一致）</li>
 *   <li>InputStream/OutputStream 是技术细节，但 {@link ExcelExporter} SPI 已依赖 I/O 流，
 *       本服务仅做决策委托，不直接操作流</li>
 *   <li>方法不返回值，调用方通过 ValidationResult 自身判断结果</li>
 * </ul>
 */
@DomainService
public class ExportDecisionService {

  /**
   * 当校验通过时导出 SplitUnit，否则静默跳过.
   *
   * @param unit     待导出的拆分单元（不能为 null）
   * @param result   校验结果（不能为 null，调用方应通过 {@link ValidationResult#isValid()} 判断）
   * @param exporter Excel 导出 SPI 实例（不能为 null）
   * @param template Excel 填充模板输入流
   * @param out      导出输出流
   * @throws IllegalArgumentException 当 unit/result/exporter 为 null 时
   */
  public void exportIfValid(SplitUnit unit, ValidationResult result,
                            ExcelExporter exporter,
                            InputStream template, OutputStream out) {
    if (unit == null) {
      throw new IllegalArgumentException("SplitUnit cannot be null");
    }
    if (result == null) {
      throw new IllegalArgumentException("ValidationResult cannot be null");
    }
    if (exporter == null) {
      throw new IllegalArgumentException("ExcelExporter cannot be null");
    }
    if (!result.isValid()) {
      return;
    }
    exporter.export(unit, template, out);
  }
}
