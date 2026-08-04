package com.example.core.api.context;

/**
 * 业务元数据超集(kernel 内部组装)
 *
 * <p>由 {@link com.example.core.adapter.context.BusinessMetaContextAssembler} 从前端 Command
 * + {@link SessionContext} 组装而成,用于传递给应用层进行批次创建等操作。
 *
 * <p>字段来源:
 * <ul>
 *   <li>{@code businessType} / {@code planNo}:来自前端 Command(办理意图)</li>
 *   <li>其余字段:来自 {@link SessionContext}(选计划时已确定,不接受前端传值)</li>
 * </ul>
 *
 * <p>注意:本类与 domain 层的
 * {@code com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext}
 * 不同——后者是流程编排引擎的配置查询上下文(含 extensionFacts),本类是 API 层的 String-based DTO。
 *
 * @author panoshu
 */
public record BusinessMetaContext(
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
