package com.example.file.types;

/**
 * 文件品类（2026-11-08 批次3 A1：与 BizType 正交的品类维度）.
 *
 * <p><b>品类 vs 业务类型</b>：BizType 回答"哪个业务"（年金新增/月度申报），
 * FileCategory 回答"什么性质的文件"——决定 file-service 的处理管线：</p>
 *
 * <table>
 *   <tr><th>品类</th><th>处理管线</th></tr>
 *   <tr><td>{@link #FORM}</td><td>上传→上下文路由解析配置→异步解析→拆分校验→FileParsedEvent</td></tr>
 *   <tr><td>{@link #MATERIAL}</td><td>上传→直存→元数据+状态管理（无内容理解）</td></tr>
 *   <tr><td>{@link #EXPORT}</td><td>业务触发→模板填充生成→OutboundReadyEvent</td></tr>
 * </table>
 *
 * <p>路由预判（设计决策 D2）：FORM 品类的 token 申请阶段即完成
 * "上下文→模板配置"路由，无可用配置立即拒绝（fail-fast）.</p>
 *
 * @param value 品类编码（FORM / MATERIAL / EXPORT）
 */
public record FileCategory(String value) {

  public static final FileCategory FORM = new FileCategory("FORM");
  public static final FileCategory MATERIAL = new FileCategory("MATERIAL");
  public static final FileCategory EXPORT = new FileCategory("EXPORT");

  public FileCategory {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("FileCategory empty");
    }
    if (!"FORM".equals(value) && !"MATERIAL".equals(value) && !"EXPORT".equals(value)) {
      throw new IllegalArgumentException("Unknown FileCategory: " + value
        + " (allowed: FORM / MATERIAL / EXPORT)");
    }
  }

  public static FileCategory of(String value) {
    return new FileCategory(value);
  }

  public boolean isForm() {
    return "FORM".equals(value);
  }
}
