package com.example.core.domain.business.repository;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.shared.domain.repository.Repository;
import com.example.shared.identifier.id.ApplicationId;
import com.example.shared.identifier.id.BatchId;
import com.example.shared.identifier.id.FormId;
import com.example.shared.identifier.id.PlanNo;

import java.util.Optional;

/**
 * 业务批次聚合根仓库
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 10:20
 */
public interface BatchRepository extends Repository<BusinessBatch, BatchId> {
  Optional<BusinessBatch> findByFormId(FormId formId);

  Optional<BusinessBatch> findByApplicationId(ApplicationId applicationId);

  /**
   * 查询指定计划+业务类型的未完成/处理中批次(活跃批次)。
   *
   * @param planNo       计划编号
   * @param businessType 业务类型
   * @return 活跃批次(若存在),只返回 CREATED 或 PROCESSING 状态的批次
   */
  Optional<BusinessBatch> findActive(PlanNo planNo, BusinessType businessType);
}
