package com.example.annuity.application.extension;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.annuity.domain.repository.AnnuityApplicationExtensionStore;
import com.example.annuity.domain.repository.AnnuityFormDraftStore;
import com.example.core.application.engine.spi.ApplicationSpawnInitializer;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.types.ApplicationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 年金申请单孵化初始化器.
 *
 * <p>表单解析裂变出申请单后、流程点火前，将表单草稿中的计划类型/初始缴费等
 * 年金扩展字段复制到 {@code applicationId} 维度；无草稿时按业务类型给出
 * 安全默认值，避免后续校验步骤因缺少扩展字段直接失败。</p>
 *
 * @author annuity-service
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnuityApplicationSpawnInitializer implements ApplicationSpawnInitializer {

  /**
   * 新建计划的最低初始缴费（分）：与 AnnuityContributionRule 保持一致。
   */
  private static final long DEFAULT_NEW_PLAN_CONTRIBUTION = 10_000L;

  private final ApplicationRepository applicationRepository;
  private final AnnuityApplicationExtensionStore extensionStore;
  private final AnnuityFormDraftStore formDraftStore;

  @Override
  public void initialize(ApplicationId applicationId) {
    BusinessApplication app = applicationRepository.loadOrThrow(applicationId);
    AnnuityApplicationExtension extension = app.formId() == null
      ? null
      : formDraftStore.load(app.formId()).orElse(null);
    if (extension == null) {
      extension = defaultExtension(app.businessContext().businessType());
    }
    extensionStore.save(applicationId, extension);
    log.info("年金申请单扩展字段初始化完成: applicationId={}, formId={}, planType={}, contribution={}",
      applicationId.value(), app.formId() != null ? app.formId().value() : null,
      extension.planType(), extension.initialContribution());
  }

  private AnnuityApplicationExtension defaultExtension(BusinessType businessType) {
    BusinessType type = businessType != null ? businessType : BusinessType.ACC_PLAN_CREATE;
    String planType = switch (type) {
      case ACC_PLAN_MODIFY -> AnnuityApplicationExtension.PLAN_TYPE_MODIFY;
      case ACC_PLAN_DELETE -> AnnuityApplicationExtension.PLAN_TYPE_DELETE;
      default -> AnnuityApplicationExtension.PLAN_TYPE_NEW;
    };
    long contribution = AnnuityApplicationExtension.PLAN_TYPE_NEW.equals(planType)
      ? DEFAULT_NEW_PLAN_CONTRIBUTION : 0L;
    return new AnnuityApplicationExtension(type, planType, contribution, false);
  }
}
