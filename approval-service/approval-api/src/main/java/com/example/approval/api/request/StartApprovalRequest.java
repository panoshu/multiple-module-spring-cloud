package com.example.approval.api.request;

import com.example.approval.types.ApprovalFlowId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 启动审批请求
 *
 * @author approval-service
 */
public record StartApprovalRequest(
  /**
   * 审批流ID
   */
  @NotNull(message = "审批流ID不能为空")
  ApprovalFlowId flowId,
  /**
   * 业务单号
   */
  @NotBlank(message = "业务单号不能为空")
  String businessNo,
  /**
   * 业务类型
   */
  @NotBlank(message = "业务类型不能为空")
  String businessType,
  /**
   * 发起人
   */
  @NotBlank(message = "发起人不能为空")
  String initiator,
  /**
   * 外部协作幂等键（spec §12.3）：kernel 传入 {@code ExternalInteraction.requestNo}。
   * 同一键重复启动只产生一个审批实例；过渡期允许为空，旧调用方经辅助构造器传 null。
   */
  String requestNo
) {

  public StartApprovalRequest(ApprovalFlowId flowId, String businessNo,
                              String businessType, String initiator) {
    this(flowId, businessNo, businessType, initiator, null);
  }
}
