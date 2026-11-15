package com.example.annuity.application.service;

import com.example.annuity.application.command.UploadFormCommand;
import com.example.annuity.application.result.ApplicationQueryResult;
import com.example.annuity.application.result.BatchQueryResult;
import com.example.annuity.application.result.UploadFormResult;
import com.example.annuity.domain.errorcode.AnnuityDomainErrorCode;
import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.annuity.domain.repository.AnnuityApplicationExtensionStore;
import com.example.annuity.domain.repository.AnnuityFormDraftStore;
import com.example.core.application.business.service.BusinessBatchAppService;
import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.business.repository.BatchRepository;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.repository.FormRepository;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.contract.IdService;
import com.example.core.types.ApplicationId;
import com.example.core.types.BatchId;
import com.example.core.types.FormId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 年金服务应用编排服务
 * <p>
 * 编排 kernel 的 {@link FlowOrchestrationService}、{@link BusinessBatchAppService}
 * 与 annuity-domain 的扩展能力，对 Adapter 层暴露年金业务的入口方法。
 * 本服务不包含复杂业务规则，仅做参数校验、结果转换和流程委派。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnuityAppService {

  private final FlowOrchestrationService orchestrationService;
  private final BusinessBatchAppService batchAppService;
  private final ApplicationRepository applicationRepository;
  private final BatchRepository batchRepository;
  private final FormRepository formRepository;
  private final IdService idService;
  private final AnnuityApplicationExtensionStore extensionStore;
  private final AnnuityFormDraftStore formDraftStore;

  /**
   * 上传年金表单
   * <p>
   * 完整流程：
   * <ol>
   *   <li>校验年金专属业务规则（计划类型、初始缴费金额）</li>
   *   <li>通过 kernel 的 {@link BusinessBatchAppService#createBatch} 创建业务批次</li>
   *   <li>通过 kernel 的 {@link BusinessForm#create} 创建业务表单并持久化</li>
   * </ol>
   * 返回批次与表单的 ID 和状态，供前端后续查询使用。
   */
  @Transactional
  public UploadFormResult uploadForm(UploadFormCommand command) {
    validateAnnuityRules(command);

    BusinessBatch batch = batchAppService.createBatch(
      command.businessContext(), command.operatorInfo()
    );

    FormId formId = idService.nextId(FormId.class);
    // 先创建空表单（WAITING_UPLOAD），前端拿到 upload-token 直传 file-service 后
    // 再调用 /annuity/core/form/confirm-upload 挂载真实 FileId。
    // 旧实现用 idService 伪造一个 FileId 直接挂载，会触发对不存在文件的解析并永久卡住表单。
    BusinessForm form = BusinessForm.create(
      formId, batch.id(), command.businessContext(), command.operatorInfo(), null
    );
    formRepository.save(form);

    // 年金专属字段暂存于表单草稿，申请单裂变时由 AnnuityApplicationSpawnInitializer 复制。
    String planType = resolvePlanType(command.planType(), command.businessContext().businessType());
    formDraftStore.save(formId, new AnnuityApplicationExtension(
      command.businessContext().businessType(),
      planType,
      resolveInitialContribution(command.initialContribution(), planType),
      Boolean.TRUE.equals(command.hasForeignInvestment())
    ));

    log.info("年金表单创建完成: batchId={}, formId={}, businessType={}, planType={}",
      batch.id().value(), form.id().value(), command.businessContext().businessType(), planType);

    return new UploadFormResult(
      batch.id().value(),
      batch.status().name(),
      form.id().value(),
      form.formStatus().name(),
      0, 0, 0,
      command.planType(),
      command.initialContribution(),
      command.hasForeignInvestment()
    );
  }

  /**
   * 查询年金申请详情
   */
  @Transactional(readOnly = true)
  public ApplicationQueryResult getApplication(ApplicationId applicationId) {
    BusinessApplication app = applicationRepository.loadOrThrow(applicationId);
    return toApplicationQueryResult(app);
  }

  /**
   * 查询年金批次状态（含关联申请单摘要）
   */
  @Transactional(readOnly = true)
  public BatchQueryResult getBatchStatus(BatchId batchId) {
    BusinessBatch batch = batchRepository.loadOrThrow(batchId);
    List<BatchQueryResult.ApplicationSummary> applications = findApplicationsByBatch(batchId);
    return toBatchQueryResult(batch, applications);
  }

  /**
   * 手动推进年金申请单流程（演示用，便于触发流程流转）
   */
  @Transactional
  public void advanceApplication(ApplicationId applicationId) {
    orchestrationService.advanceStep(applicationId);
  }

  /**
   * 处理文件解析完成事件（由 FileParsedEventListener 调用）
   */
  @Transactional
  public void handleFileParsed(String fileTaskId) {
    orchestrationService.advanceByFileTaskId(fileTaskId);
  }

  /**
   * 处理审批结果事件（由 ApprovalResultEventListener 调用）
   */
  @Transactional
  public void handleApprovalResult(String businessNo, String result) {
    orchestrationService.advanceByApprovalResult(businessNo, result);
  }

  // ==========================================
  // 私有方法：业务规则校验
  // ==========================================

  private void validateAnnuityRules(UploadFormCommand command) {
    validateInitialContribution(command.initialContribution());
    validatePlanType(command.planType());
  }

  private void validateInitialContribution(Long initialContribution) {
    if (initialContribution != null && initialContribution < 0) {
      throw new DomainException(AnnuityDomainErrorCode.INVALID_CONTRIBUTION)
        .withLogDetail("初始缴费金额不能为负: " + initialContribution);
    }
  }

  private void validatePlanType(String planType) {
    if (planType == null) {
      return;
    }
    boolean valid = AnnuityApplicationExtension.PLAN_TYPE_NEW.equals(planType)
      || AnnuityApplicationExtension.PLAN_TYPE_MODIFY.equals(planType)
      || AnnuityApplicationExtension.PLAN_TYPE_DELETE.equals(planType);
    if (!valid) {
      throw new DomainException(AnnuityDomainErrorCode.UNSUPPORTED_PLAN_TYPE)
        .withLogDetail("不支持的计划类型: " + planType);
    }
  }

  /**
   * 计划类型默认值：请求未显式传入时由业务类型推导，避免裂变后的校验步骤缺少扩展字段。
   */
  private String resolvePlanType(String requestedPlanType, BusinessType businessType) {
    if (requestedPlanType != null && !requestedPlanType.isBlank()) {
      return requestedPlanType;
    }
    if (businessType == BusinessType.ACC_PLAN_MODIFY) {
      return AnnuityApplicationExtension.PLAN_TYPE_MODIFY;
    }
    if (businessType == BusinessType.ACC_PLAN_DELETE) {
      return AnnuityApplicationExtension.PLAN_TYPE_DELETE;
    }
    return AnnuityApplicationExtension.PLAN_TYPE_NEW;
  }

  /**
   * 初始缴费默认值：新建计划默认 100 元（10000 分），与领域校验规则一致。
   */
  private Long resolveInitialContribution(Long requestedContribution, String requestedPlanType) {
    if (requestedContribution != null) {
      return requestedContribution;
    }
    return AnnuityApplicationExtension.PLAN_TYPE_NEW.equals(requestedPlanType) || requestedPlanType == null
      ? 10_000L : 0L;
  }

  // ==========================================
  // 私有方法：领域对象 → Result 转换
  // ==========================================

  private ApplicationQueryResult toApplicationQueryResult(BusinessApplication app) {
    BusinessMetaContext ctx = app.buildConfigQueryContext();
    AnnuityApplicationExtension ext = readAnnuityExtension(app);

    return new ApplicationQueryResult(
      app.id().value(),
      app.batchId() != null ? app.batchId().value() : null,
      app.formId() != null ? app.formId().value() : null,
      ctx != null && ctx.businessType() != null ? ctx.businessType().name() : null,
      ctx != null && ctx.customerNo() != null ? ctx.customerNo().value() : null,
      ctx != null && ctx.productNo() != null ? ctx.productNo().value() : null,
      ctx != null && ctx.planNo() != null ? ctx.planNo().value() : null,
      app.status() != null ? app.status().name() : null,
      app.currentStep() != null ? app.currentStep().name() : null,
      ext != null ? ext.planType() : null,
      ext != null ? ext.initialContribution() : null,
      ext != null ? ext.hasForeignInvestment() : null,
      app.createdBy(),
      app.createdAt(),
      app.updatedBy(),
      app.updatedAt()
    );
  }

  private BatchQueryResult toBatchQueryResult(BusinessBatch batch,
                                              List<BatchQueryResult.ApplicationSummary> applications) {
    return new BatchQueryResult(
      batch.id().value(),
      batch.status() != null ? batch.status().name() : null,
      null,
      null,
      batch.totalApplicationCount(),
      batch.successCount(),
      batch.failedCount(),
      applications
    );
  }

  /** 从业务服务自有存储按 applicationId 读取年金扩展字段（§6.2）。 */
  private AnnuityApplicationExtension readAnnuityExtension(BusinessApplication app) {
    return extensionStore.load(app.id()).orElse(null);
  }

  private List<BatchQueryResult.ApplicationSummary> findApplicationsByBatch(BatchId batchId) {
    return applicationRepository.findByBatchId(batchId).stream()
      .map(app -> new BatchQueryResult.ApplicationSummary(
        app.id().value(),
        app.businessContext() != null && app.businessContext().businessType() != null
          ? app.businessContext().businessType().name() : null,
        app.status() != null ? app.status().name() : null,
        app.currentStep() != null ? app.currentStep().name() : null
      ))
      .toList();
  }

}
