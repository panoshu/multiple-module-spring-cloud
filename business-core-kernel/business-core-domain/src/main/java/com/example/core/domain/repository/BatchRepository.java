package com.example.core.domain.repository;

import com.example.core.domain.aggregate.root.BusinessBatch;
import com.example.shared.domain.repository.Repository;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FormId;

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
}
