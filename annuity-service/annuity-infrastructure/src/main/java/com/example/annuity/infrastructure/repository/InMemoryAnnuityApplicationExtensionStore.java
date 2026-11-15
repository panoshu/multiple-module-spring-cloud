package com.example.annuity.infrastructure.repository;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.annuity.domain.repository.AnnuityApplicationExtensionStore;
import com.example.core.types.ApplicationId;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 年金业务申请扩展字段的内存态实现（spec §6.2，批次 37b）。
 * <p>
 * annuity 是早期示例服务，采用进程内存储即可满足 E2E 验证；
 * 真实业务服务应实现为持久化的自有聚合，通过 {@code applicationId} 关联。
 *
 * @author annuity-service
 */
@Repository
public class InMemoryAnnuityApplicationExtensionStore implements AnnuityApplicationExtensionStore {

  private final Map<ApplicationId, AnnuityApplicationExtension> extensions = new ConcurrentHashMap<>();

  @Override
  public void save(ApplicationId applicationId, AnnuityApplicationExtension extension) {
    if (applicationId == null || extension == null) {
      return;
    }
    extensions.put(applicationId, extension);
  }

  @Override
  public Optional<AnnuityApplicationExtension> load(ApplicationId applicationId) {
    if (applicationId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(extensions.get(applicationId));
  }
}
