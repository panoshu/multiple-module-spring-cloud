package com.example.core.domain.business.repository;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.shared.domain.repository.Repository;
import com.example.core.types.ApplicationId;
import com.example.core.types.BatchId;
import com.example.core.types.FormId;
import com.example.core.types.PlanNo;

import java.util.Collection;
import java.util.List;
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

  /**
   * 按状态批量查询批次，供恢复任务按状态索引扫描，避免全表 {@code loadAll()}。
   *
   * @param statuses 目标状态集合，空集合直接返回空列表
   * @return 匹配状态且未逻辑删除的批次
   */
  List<BusinessBatch> findByStatuses(Collection<BatchStatus> statuses);
}
