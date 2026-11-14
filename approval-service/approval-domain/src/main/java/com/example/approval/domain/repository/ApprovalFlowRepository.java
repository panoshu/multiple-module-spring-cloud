package com.example.approval.domain.repository;

import com.example.approval.domain.aggregate.root.ApprovalFlow;
import com.example.approval.domain.valueobject.ApprovalFlowPage;
import com.example.approval.domain.valueobject.FlowVersion;
import com.example.approval.domain.valueobject.MatchRules;
import com.example.approval.types.ApprovalFlowId;
import com.example.approval.types.enums.FlowStatus;
import com.example.shared.domain.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 审批流仓储接口
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public interface ApprovalFlowRepository extends Repository<ApprovalFlow, ApprovalFlowId> {

  /**
   * 根据审批流ID加载审批流
   *
   * @param flowId 审批流ID
   * @return 审批流（可能为空）
   */
  @Override
  Optional<ApprovalFlow> load(ApprovalFlowId flowId);

  /**
   * 根据审批流ID和版本加载审批流
   *
   * @param flowId  审批流ID
   * @param version 版本号
   * @return 审批流（可能为空）
   */
  Optional<ApprovalFlow> load(ApprovalFlowId flowId, FlowVersion version);

  /**
   * 保存审批流
   *
   * @param flow 审批流
   */
  @Override
  void save(ApprovalFlow flow);

  /**
   * 根据状态查找审批流列表
   *
   * @param status 审批流状态
   * @return 审批流列表
   */
  List<ApprovalFlow> findByStatus(FlowStatus status);

  /**
   * 根据匹配规则查找激活的审批流列表
   *
   * @param rules 匹配规则
   * @return 审批流列表
   */
  List<ApprovalFlow> findActiveByMatchRules(MatchRules rules);

  /**
   * 按条件分页查询审批流（批次 54，P3-1 能力 B）。
   * <p>
   * 业务类型与状态均为可选过滤条件（空值表示不过滤）；仓储在 DB 侧完成计数与
   * limit/offset 分页，走 {@code idx_approval_flow_business_type} /
   * {@code idx_approval_flow_status} / {@code idx_approval_flow_create_time} 索引。
   *
   * @param businessType 业务类型（可选）
   * @param status       状态字符串（可选，ACTIVE / DEPRECATED）
   * @param startPos     起始偏移（从 0 开始）
   * @param pageSize     每页条数
   * @return 总数与当前页的审批流列表
   */
  ApprovalFlowPage findByCondition(String businessType, String status, int startPos, int pageSize);
}
