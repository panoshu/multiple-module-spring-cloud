package com.example.annuity.domain.repository;

import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.types.AnnuityEmployeeBatchId;
import com.example.shared.domain.repository.Repository;
import com.example.shared.identifier.id.ApplicationId;

import java.util.Optional;

/**
 * 年金员工明细批次仓储接口
 *
 * @author annuity-service
 * @since 2026/7/22
 */
public interface AnnuityEmployeeBatchRepository extends Repository<AnnuityEmployeeBatch, AnnuityEmployeeBatchId> {

  /**
   * 根据申请单 ID 反查批次
   *
   * @param applicationId 申请单 ID
   * @return 批次(可能为空)
   */
  Optional<AnnuityEmployeeBatch> findByApplicationId(ApplicationId applicationId);
}
