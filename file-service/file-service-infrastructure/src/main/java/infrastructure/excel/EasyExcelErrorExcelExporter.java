// 文件路径: src/main/java/infrastructure/excel/EasyExcelErrorExcelExporter.java
package infrastructure.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.excel.write.metadata.WriteSheet;
import core.domain.model.ErrorRecord;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class EasyExcelErrorExcelExporter {

  public void exportErrorExcel(InputStream originalStream, List<ErrorRecord> errors, OutputStream errorOutputStream) {

    // 1. 将错误集合按行号分组，并将同一行的多个错误合并
    Map<Integer, String> errorMap = errors.stream()
      .filter(e -> e.rowIndex() > 0)
      .collect(Collectors.groupingBy(
        ErrorRecord::rowIndex,
        Collectors.mapping(ErrorRecord::message, Collectors.joining(" | "))
      ));

    // 2. 构建流式写出器
    try (ExcelWriter excelWriter = EasyExcel.write(errorOutputStream).build()) {
      WriteSheet writeSheet = EasyExcel.writerSheet(0, "错题本").build();

      EasyExcel.read(originalStream, new AnalysisEventListener<Map<Integer, String>>() {

        private int maxColumnIndex = 0;

        @Override
        public void invoke(Map<Integer, String> rowData, AnalysisContext context) {
          int rowIndex = context.readRowHolder().getRowIndex() + 1; // 1-based 物理行号

          // 动态探测当前行的最大列索引
          int currentRowMaxCol = rowData.keySet().stream().max(Integer::compareTo).orElse(-1);
          if (currentRowMaxCol > maxColumnIndex) {
            maxColumnIndex = currentRowMaxCol;
          }

          int errorColIndex = maxColumnIndex + 1;

          // ===== ★ 核心修复：多行表头完美复刻与对称 =====
          if (rowIndex == 5) {
            // 在第 5 行（原表单的字段ID行），写入错误列的英文标识
            rowData.put(errorColIndex, "ERROR_MSG");
          } else if (rowIndex == 6) {
            // 在第 6 行（原表单的中文分类行），写入分类标签
            rowData.put(errorColIndex, "系统校验");
          } else if (rowIndex == 7) {
            // 在第 7 行（原表单的中文名称行），写入最终的列名
            rowData.put(errorColIndex, "错误信息提示");
          } else if (rowIndex < 5) {
            // 前 4 行是原表单的废话说明或头信息标签，错误列保持空字符串即可，维持排版不乱
            rowData.put(errorColIndex, "");
          } else if (errorMap.containsKey(rowIndex)) {
            // 第 8 行及以后的真实明细行，如果报错则写入汉化后的错误大白话
            rowData.put(errorColIndex, errorMap.get(rowIndex));
          }

          // 实时单行写出，绝不占用多余内存
          excelWriter.write(Collections.singletonList(rowData), writeSheet);
        }

        @Override
        public void doAfterAllAnalysed(AnalysisContext context) {
        }
      }).headRowNumber(0).sheet().doRead();
    }
  }
}
