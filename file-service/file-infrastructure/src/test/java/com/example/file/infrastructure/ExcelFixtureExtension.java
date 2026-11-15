package com.example.file.infrastructure;

import org.apache.fesod.sheet.FesodSheet;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Excel 测试夹具生成扩展.
 *
 * <p>{@code *.xlsx} 被 .gitignore 排除，测试不依赖仓库中的二进制文件；
 * 首次运行前在 {@code docs/excel/} 下生成两份稳定夹具：</p>
 * <ul>
 *   <li>{@code 示例表单.xlsx}：企业计划标准表单（ConfigDriven/Parse/Export 测试）</li>
 *   <li>{@code annuity_form_detail.xlsx}：年金 FORM_DETAIL 表单（模板播种契约测试）</li>
 * </ul>
 *
 * <p>生成逻辑与 {@code docs/模板配置/配置示例/} 中的 YAML 保持契约一致；
 * 若需调整结构，请同步修改 YAML 与本扩展，并重跑相关测试。</p>
 */
public class ExcelFixtureExtension implements BeforeAllCallback {

  private static final Path DIR = Path.of("docs", "excel");
  private static final Path ENTERPRISE = DIR.resolve("示例表单.xlsx");
  private static final Path ANNUITY = DIR.resolve("annuity_form_detail.xlsx");

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    Files.createDirectories(DIR);
    if (!Files.exists(ENTERPRISE)) {
      writeEnterpriseWorkbook();
    }
    if (!Files.exists(ANNUITY)) {
      writeAnnuityWorkbook();
    }
  }

  private void writeEnterpriseWorkbook() {
    List<List<Object>> rows = new ArrayList<>();
    rows.add(Arrays.asList("企业计划编号：", "0200010001", "企业计划名称：", "企业计划A"));
    rows.add(Arrays.asList("企业客户号：", "000234", "企业客户名称：", "客户A"));
    // Fesod 读取会跳过空行；非空说明占位保证交付索引 2=说明、3=XH。
    rows.add(Arrays.asList("请在填写表单之前仔细阅读填表说明", "", "", ""));
    rows.add(Arrays.asList("XH", "XM", "ZJLX", "ZJHM", "BZ"));
    rows.add(Arrays.asList("基本信息", "", "", "", ""));
    rows.add(Arrays.asList("序号*", "个人姓名*", "证件类型*", "证件号码*", "备注*"));
    rows.add(Arrays.asList("1", "张内Aa01", "身份证", "999000198608060000", "备注1"));
    rows.add(Arrays.asList("2", "张内Aa02", "护照", "999000198608060000", "备注2"));
    rows.add(Arrays.asList("3", "张内Aa03", "身份证", "999000198608060000", "备注3"));
    rows.add(Arrays.asList("结束", "如果在提交表单时本行以上部分存在空行，请删除空行", "", ""));
    rows.add(Arrays.asList("填表人:", "张三", "复核人：", "李四"));
    rows.add(Arrays.asList("填表说明", "", "", ""));
    rows.add(Arrays.asList("填表说明", "", "", ""));
    rows.add(Arrays.asList("填表说明", "", "", ""));
    FesodSheet.write(ENTERPRISE.toString()).sheet().doWrite(rows);
  }

  private void writeAnnuityWorkbook() {
    List<List<Object>> rows = new ArrayList<>();
    rows.add(Arrays.asList("计划编号：", "PLAN-2026-001", "客户编号：", "CUST-001", "客户名称：", "示例客户"));
    rows.add(Arrays.asList("", "", "", "", "", ""));
    rows.add(Arrays.asList("员工姓名", "身份证号", "年龄", "月薪(分)", "月缴费(分)"));
    rows.add(Arrays.asList("张三", "110101199001010011", "30", "2000000", "200000"));
    rows.add(Arrays.asList("李四", "110101199202020022", "28", "1800000", "180000"));
    rows.add(Arrays.asList("结束", "", "", "", ""));
    FesodSheet.write(ANNUITY.toString()).sheet().doWrite(rows);
  }
}
