package com.example.core.application.business.service;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.enums.status.ApplicationStatus;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.business.repository.BatchRepository;
import com.example.shared.identifier.contract.IdService;
import com.example.core.types.BatchId;
import com.example.core.types.Channel;
import com.example.core.types.FormId;
import com.example.core.types.PlanNo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 业务批次应用服务
 *
 * <p>编排批次的创建、查询、取消等业务流程,管理事务边界。
 *
 * <p>后续新增应用服务方法流程:
 * <ol>
 *   <li>在领域层聚合根/Repository 中定义行为/查询方法</li>
 *   <li>在本类中编排:加载聚合根 → 调用领域行为 → 保存 → 发布事件</li>
 *   <li>事务边界由本类管理(@Transactional)</li>
 * </ol>
 *
 * @author panoshu
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BusinessBatchAppService {

  private static final OperatorInfo SYSTEM_OPERATOR =
    new OperatorInfo(Channel.NETAPP, "SYSTEM", "系统对账", false);

  private final BatchRepository batchRepository;
  private final ApplicationRepository applicationRepository;
  private final IdService idService;

  /**
   * 创建业务批次。
   *
   * @param context  业务上下文(从 SessionClaims 组装)
   * @param operator 操作人信息
   * @return 创建的批次聚合根
   */
  @Transactional
  public BusinessBatch createBatch(BusinessContext context, OperatorInfo operator) {
    BatchId batchId = idService.nextId(BatchId.class);
    BusinessBatch batch = BusinessBatch.create(batchId, context, operator);
    batchRepository.save(batch);    log.info("创建业务批次成功: batchId={}, businessType={}", batchId.value(), context.businessType());
    return batch;
  }

  /**
   * 查询指定计划+业务类型的活跃批次(未完成/处理中)。
   *
   * @param planNo       计划编号
   * @param businessType 业务类型
   * @return 活跃批次(若存在)
   */
  @Transactional(readOnly = true)
  public Optional<BusinessBatch> findActive(PlanNo planNo, BusinessType businessType) {
    return batchRepository.findActive(planNo, businessType);
  }

  /**
   * 通过表单 ID 反查批次。
   */
  @Transactional(readOnly = true)
  public Optional<BusinessBatch> findByFormId(FormId formId) {
    return batchRepository.findByFormId(formId);
  }

  /**
   * 加载批次(不存在时抛异常)。
   */
  @Transactional(readOnly = true)
  public BusinessBatch loadOrThrow(BatchId batchId) {
    return batchRepository.loadOrThrow(batchId);
  }

  /**
   * 取消批次。
   *
   * @param batchId 批次ID
   * @param reason  取消原因
   */
  @Transactional
  public void cancel(BatchId batchId, String reason) {
    BusinessBatch batch = batchRepository.loadOrThrow(batchId);
    batch.cancel(reason);
    batchRepository.save(batch);    log.info("取消业务批次: batchId={}, reason={}", batchId.value(), reason);
  }

  /**
   * 按批次下全部申请的真实终态重算批次计数与状态（§6.4 对账能力）。
   * <p>
   * 以申请聚合为权威数据源做绝对赋值，重复触发不会重复计数；统计口径为
   * COMPLETED 计成功，FAILED/REJECTED/WITHDRAWN/CANCELLED 计失败，其余在途不计。
   */
  @Transactional
  public BusinessBatch reconcileStatistics(BatchId batchId, OperatorInfo operator) {
    BusinessBatch batch = batchRepository.loadOrThrow(batchId);
    List<BusinessApplication> applications = applicationRepository.findByBatchId(batchId);
    int total = applications == null ? 0 : applications.size();
    int success = 0;
    int failed = 0;
    if (applications != null) {
      for (BusinessApplication application : applications) {
        ApplicationStatus status = application.status();
        if (status == null) {
          continue;
        }
        switch (status.normalized()) {
          case COMPLETED -> success++;
          case FAILED, REJECTED, WITHDRAWN, CANCELLED -> failed++;
          default -> { }
        }
      }
    }
    if (batch.reconcile(total, success, failed, operator)) {
      batchRepository.save(batch);
    }
    return batch;
  }

  /**
   * 恢复任务入口：按状态索引扫描活跃批次并逐个对账（§19.2「重算批次统计」）。
   * 查询收敛为 CREATED/PROCESSING 两个状态（走 idx_business_batch_status），
   * 不再全表 loadAll；单条失败隔离，批次间不共享事务，与既有恢复动作语义一致。
   */
  public int reconcileAllActive() {
    List<BusinessBatch> candidates = batchRepository.findByStatuses(
      List.of(BatchStatus.CREATED, BatchStatus.PROCESSING));
    if (candidates == null || candidates.isEmpty()) {
      return 0;
    }
    int reconciled = 0;
    for (BusinessBatch candidate : candidates) {
      if (candidate == null || !candidate.status().isActive()) {
        continue;
      }
      try {
        reconcileStatistics(candidate.id(), SYSTEM_OPERATOR);
        reconciled++;
      } catch (Exception e) {
        log.error("重算批次统计失败（单条隔离）: batchId={}", candidate.id(), e);
      }
    }
    return reconciled;
  }
}
