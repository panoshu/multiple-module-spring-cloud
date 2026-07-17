package com.example.core.domain.repository;

import com.example.core.domain.aggregate.root.BusinessForm;
import com.example.shared.domain.repository.Repository;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.FormId;

import java.util.Optional;

/**
 * 业务批次表单聚合根仓库
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/14 10:20
 */
public interface FormRepository extends Repository<BusinessForm, FormId> {
  Optional<BusinessForm> findByApplicationId(ApplicationId applicationId);
}
