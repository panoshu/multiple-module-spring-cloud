package com.example.core.application.business.service;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.repository.BatchRepository;
import com.example.shared.domain.event.EventBus;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.identifier.id.BatchId;
import com.example.shared.identifier.id.FormId;
import com.example.shared.identifier.id.PlanNo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

  private final BatchRepository batchRepository;
  private final EventBus eventBus;
  private final IdService idService;

  /**
   * 创建业务批次。
   *
   * @param context  业务上下文(从 SessionContext 组装)
   * @param operator 操作人信息
   * @return 创建的批次聚合根
   */
  @Transactional
  public BusinessBatch createBatch(BusinessContext context, OperatorInfo operator) {
    BatchId batchId = idService.nextId(BatchId.class);
    BusinessBatch batch = BusinessBatch.create(batchId, context, operator);
    batchRepository.save(batch);
    batch.domainEvents().forEach(eventBus::publish);
    batch.clearDomainEvents();
    log.info("创建业务批次成功: batchId={}, businessType={}", batchId.value(), context.businessType());
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
    batchRepository.save(batch);
    batch.domainEvents().forEach(eventBus::publish);
    batch.clearDomainEvents();
    log.info("取消业务批次: batchId={}, reason={}", batchId.value(), reason);
  }
}
