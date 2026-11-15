package com.example.annuity.infrastructure.repository;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.annuity.domain.repository.AnnuityFormDraftStore;
import com.example.core.types.FormId;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 年金表单草稿扩展字段的进程内实现（示例服务）.
 *
 * @author annuity-service
 */
@Repository
public class InMemoryAnnuityFormDraftStore implements AnnuityFormDraftStore {

  private final Map<FormId, AnnuityApplicationExtension> drafts = new ConcurrentHashMap<>();

  @Override
  public void save(FormId formId, AnnuityApplicationExtension draft) {
    if (formId == null || draft == null) {
      return;
    }
    drafts.put(formId, draft);
  }

  @Override
  public Optional<AnnuityApplicationExtension> load(FormId formId) {
    if (formId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(drafts.get(formId));
  }
}
