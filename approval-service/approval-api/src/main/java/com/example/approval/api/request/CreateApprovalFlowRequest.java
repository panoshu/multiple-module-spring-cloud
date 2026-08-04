package com.example.approval.api.request;

import com.example.approval.api.dto.ApprovalNodeDTO;
import com.example.approval.api.dto.MatchRulesDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 创建审批流请求
 *
 * @author approval-service
 */
public record CreateApprovalFlowRequest(
  /**
   * 审批流名称
   */
  @NotBlank(message = "审批流名称不能为空")
  String flowName,
  /**
   * 业务类型
   */
  @NotBlank(message = "业务类型不能为空")
  String businessType,
  /**
   * 匹配规则
   */
  @NotNull(message = "匹配规则不能为空")
  @Valid
  MatchRulesDTO matchRules,
  /**
   * 审批节点列表
   */
  @NotEmpty(message = "审批节点列表不能为空")
  @Valid
  List<ApprovalNodeDTO> nodes,
  /**
   * 创建人
   */
  @NotBlank(message = "创建人不能为空")
  String createdBy
) {
}
