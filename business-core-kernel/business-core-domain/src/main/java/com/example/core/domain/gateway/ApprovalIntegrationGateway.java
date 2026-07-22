package com.example.core.domain.gateway;

import com.example.core.domain.business.aggregate.root.BusinessApplication;

/**
 * 审批集成网关 SPI (防腐层)
 * <p>
 * 屏蔽底层审批服务 (approval-service) 的技术细节，由各业务模块在 infrastructure 层实现。
 * 调用 approval-service 完成审批流匹配与实例启动，并通过实例 ID 查询审批状态。
 *
 * <h3>使用场景</h3>
 * <ul>
 * <li><b>流程节点触发审批</b>：在审批步骤中，Handler 调用本网关发起审批并挂起流程。</li>
 * <li><b>审批回调唤醒</b>：审批服务完成审批后，通过事件回调携带实例 ID，
 * 应用层通过 {@link #queryApprovalStatus(String)} 查询结果决定后续流转。</li>
 * </ul>
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 18:40
 */
public interface ApprovalIntegrationGateway {

  /**
   * 匹配审批流并启动审批实例
   * <p>
   * 实现内部依次完成：
   * <ol>
   * <li>根据业务类型、账管人匹配审批流；</li>
   * <li>使用业务单号启动审批实例。</li>
   * </ol>
   *
   * @param application 业务申请聚合根 (提供业务单号、业务类型、发起人等)
   * @return 审批实例 ID (用于后续状态查询)
   */
  String startApproval(BusinessApplication application);

  /**
   * 查询审批实例状态
   *
   * @param instanceId 审批实例 ID (由 {@link #startApproval} 返回)
   * @return 审批状态字符串：PENDING / APPROVING / APPROVED / REJECTED / WITHDRAWN
   */
  String queryApprovalStatus(String instanceId);
}
