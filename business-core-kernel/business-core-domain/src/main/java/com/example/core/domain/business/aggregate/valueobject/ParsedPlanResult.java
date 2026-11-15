package com.example.core.domain.business.aggregate.valueobject;

import com.example.core.types.FileRefId;
import com.example.core.types.PlanNo;

/**
 * 表单解析拆分结果 (按业务计划维度)
 * <p>
 * 当底层文档服务完成复杂 Excel 表单的解析、拆分和基础 Schema 校验后，
 * 将为每一个拆分维度（如：不同的企业计划）生成一个独立的 JSON 文件，
 * 并通过此对象将结果回调给核心业务域。
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/17 13:16
 */
public record ParsedPlanResult(

  /*
   * 解析出的业务计划号/产品号。
   * (例如：这份总表单里，拆分出了针对 "PLAN-888" 的数据)
   */
  PlanNo planId,

  /*
   * 该计划对应的明细 JSON 文件凭证。
   * (业务后续将通过这个 FileRefId 进行流式读取和明细落库)
   */
  FileRefId jsonFileId,

  /*
   * 解析出的有效明细总条数。
   * (极其重要：用于后续流式摄入完成后的【数据对账】，防止丢数据)
   */
  int totalRecordCount,

  /*
   * file-service 当前的标准数据子任务凭证（ParsedDataApi.fetchRows 的入参）。
   * 旧版 JSON 文件模式可空，由 jsonFileId 兼容。
   */
  String subTaskId
) {

  /**
   * 兼容旧调用：仅携带 JSON 文件凭证的解析结果。
   */
  public ParsedPlanResult(PlanNo planId, FileRefId jsonFileId, int totalRecordCount) {
    this(planId, jsonFileId, totalRecordCount, null);
  }
}
