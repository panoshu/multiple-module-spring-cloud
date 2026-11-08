package com.example.annuity.application.command;

import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;

/**
 * 年金表单上传命令（应用层 CQE 对象）
 * <p>
 * 由 Adapter 层通过 MapStruct Converter 从 {@code UploadFormRequest} 转换而来，
 * 注入领域原语（{@link BusinessContext}、{@link OperatorInfo}）后交由
 * {@code AnnuityAppService} 编排处理。
 * <p>
 * 与 API 层 {@code UploadFormRequest} 的区别：
 * <ul>
 *   <li>Command 携带领域原语（BusinessContext/OperatorInfo），可直接被领域层使用</li>
 *   <li>Request 携带原始 String/Long，需要 Adapter 层做类型转换和校验</li>
 * </ul>
 *
 * @author annuity-service
 * @since 2026/7/21
 */
public record UploadFormCommand(
  BusinessContext businessContext,
  OperatorInfo operatorInfo,
  String fileName,
  Long fileSize,
  String planType,
  Long initialContribution,
  Boolean hasForeignInvestment
) {
}
