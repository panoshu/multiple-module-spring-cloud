package com.example.approval.domain.repository;

import com.example.approval.domain.aggregate.root.ApprovalInstance;
import com.example.approval.types.ApprovalInstanceId;
import com.example.approval.types.enums.InstanceStatus;
import com.example.shared.domain.repository.Repository;
import com.example.core.types.ApplicationId;

import java.util.Collection;
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

  /** 按外部协作幂等键查询（spec §12.3）：用于重复启动幂等与响应丢失对账。 */
  Optional<ApprovalInstance> findByRequestNo(String requestNo);

  /**
   * 按状态批量查询审批实例（批次 55，P3-1 能力 C）。
   * <p>
   * 待审批列表需按「当前节点审批人」过滤，而该信息在静态审批流节点定义中、
   * 实例表无审批人字段，无法在 DB 侧直接过滤；故仓储只按状态取出活跃实例，
   * 由应用层结合节点定义过滤并内存分页。走 {@code idx_approval_instance_status} 索引。
   *
   * @param statuses 状态集合（空集合直接返回空列表）
   * @return 匹配状态的审批实例列表（按创建时间降序）
   */
  List<ApprovalInstance> findByStatuses(Collection<InstanceStatus> statuses);
}
