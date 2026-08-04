package com.example.core.api.batch.command;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建业务批次命令
 *
 * <p>前端只传办理意图(businessType + planNo),客户/产品/账管人等敏感字段
 * 由后端从 SessionContext 组装,杜绝前端伪造。
 *
 * @author panoshu
 */
public record CreateBatchCommand(
  @NotBlank(message = "业务类型不能为空") String businessType,
  @NotBlank(message = "计划编号不能为空") String planNo,
  String operatorRemark
) {
}
