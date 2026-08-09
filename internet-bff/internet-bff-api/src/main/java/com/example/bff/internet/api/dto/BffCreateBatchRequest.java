package com.example.bff.internet.api.dto;

import com.example.core.api.batch.command.CreateBatchCommand;
import jakarta.validation.constraints.NotBlank;

/**
 * 创建批次请求
 *
 * @author bff
 */
public record BffCreateBatchRequest(
  @NotBlank(message = "业务类型不能为空") String businessType,
  @NotBlank(message = "计划编号不能为空") String planNo,
  String operatorRemark
) {
  public CreateBatchCommand toCommand() {
    return new CreateBatchCommand(businessType, planNo, operatorRemark);
  }
}
