package com.example.file.domain.gateway;

import com.example.file.domain.model.valueobject.SplitUnit;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * 基于 Excel 模板填充 SplitUnit 数据并写入输出流。
 *
 * <p>模板占位符语法（由实现决定，当前实现用 fesod）：
 * <ul>
 *   <li>{@code {fieldName}} - 普通变量，从 SplitUnit.data 顶层取值</li>
 *   <li>{@code {listName.field}} - 列表变量，从 SplitUnit.data 顶层的 List&lt;Map&gt; 取字段</li>
 * </ul>
 */
public interface ExcelExporter {
  void export(SplitUnit unit, InputStream templateStream, OutputStream out);
}
