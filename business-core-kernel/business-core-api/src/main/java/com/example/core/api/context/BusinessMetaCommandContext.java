package com.example.core.api.context;

/**
 * 业务元数据命令上下文（API 层，kernel 内部组装）
 *
 * <p>由 {@link com.example.core.adapter.context.BusinessMetaContextAssembler} 从前端 Command
 * + {@code SessionClaims} 组装而成,用于传递给应用层进行批次创建等操作。
 *
 * <p>字段来源:
 * <ul>
 *   <li>{@code businessType} / {@code planNo}:来自前端 Command(办理意图)</li>
 *   <li>其余字段:来自 {@code SessionClaims}(选计划时已确定,不接受前端传值)</li>
 * </ul>
 *
 * <p><b>命名说明（K-09）</b>：本类原名 {@code BusinessMetaContext}，与 domain 层
 * {@code com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext}
 * 同名。domain 类是流程编排引擎的配置查询上下文（含 extensionFacts），
 * 本类是 API 层基于 String 的命令上下文。重命名以消除同名歧义。</p>
 *
 * @author panoshu
 */
public record BusinessMetaCommandContext(
  String businessType,
  String planNo,
  String customerNo,
  String customerName,
  String productNo,
  String productName,
  String planName,
  String operationModel,
  String accountManager
) {
}
