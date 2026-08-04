package com.example.approval.domain.repository;

import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.types.ApprovalInstanceId;
import com.example.approval.types.enums.InstanceStatus;
import com.example.shared.domain.repository.Repository;
import com.example.shared.identifier.id.ApplicationId;
import com.example.shared.identifier.id.UserNo;

import java.util.List;
import java.util.Optional;

/**
 * 审批实例仓储接口
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/7/14
 */
public interface ApprovalInstanceRepository extends Repository<ApprovalInstance, ApprovalInstanceId> {

  /**
   * 根据审批实例ID加载审批实例
   *
   * @param instanceId 审批实例ID
   * @return 审批实例（可能为空）
   */
  @Override
  Optional<ApprovalInstance> load(ApprovalInstanceId instanceId);

  /**
   * 保存审批实例
   *
   * @param instance 审批实例
   */
  @Override
  void save(ApprovalInstance instance);

  /**
   * 根据业务申请ID查找审批实例
   *
   * @param applicationId 业务申请ID
   * @return 审批实例（可能为空）
   */
  Optional<ApprovalInstance> findByBusinessApplicationId(ApplicationId applicationId);

  /**
   * 根据审批人ID和状态查找审批实例列表
   *
   * @param approverId 审批人ID
   * @param status     实例状态
   * @return 审批实例列表
   */
  List<ApprovalInstance> findByApproverId(UserNo approverId, InstanceStatus status);
}
