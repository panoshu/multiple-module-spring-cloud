package com.example.core.domain.business.aggregate.root;

import com.example.core.domain.business.aggregate.valueobject.*;
import com.example.core.domain.business.aggregate.valueobject.enums.status.ApplicationStatus;
import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.domain.business.event.ApplicationSpawnedEvent;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.PipelineExecutionResult;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.core.domain.engine.event.PipelineExecutedEvent;
import com.example.core.domain.engine.event.StepEnteredEvent;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.core.types.ApplicationId;
import com.example.core.types.BatchId;
import com.example.core.types.FormId;
import com.example.file.types.FileId;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务申请，对应每个表单中的一笔企业计划维度的申请
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/13 12:29
 */
public class BusinessApplication extends AggregateRoot<ApplicationId> {

  private BatchId batchId;
  private FormId formId;
  private DefinitionVersion definitionVersion;

  private BusinessContext businessContext;
  private OperatorInfo operatorInfo;

  private FileId parsedJsonFileId;
  private int expectedDetailCount;

  // TODO 状态与流程状态处理
  private ApplicationStatus status; // 宏观状态
  private ApplicationFlowStep currentStep;

  private LocalDateTime applyTime;
  private LocalDateTime completeTime;

  /** 可选材料能力状态（§6.2，批次 37a）：核心聚合不再直接持有材料清单与打包文件。 */
  private ApplicationMaterialState materialState = ApplicationMaterialState.empty();

  private BusinessFile acceptanceFile;

  protected BusinessApplication(ApplicationId applicationId, String userNo) {
    super(applicationId, userNo);
    this.validateInvariants();
  }

  protected BusinessApplication(ApplicationId applicationId, String createdBy, String updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(applicationId, createdBy, updatedBy, createdAt, updatedAt, version);
    this.validateInvariants();
  }

  protected BusinessApplication(ApplicationId applicationId, BusinessContext businessContext, OperatorInfo operatorInfo, FileId jsonFileId) {
    super(applicationId, operatorInfo.operatorId());
    this.businessContext = businessContext;
    this.operatorInfo = operatorInfo;
    this.parsedJsonFileId = jsonFileId;
    this.validateInvariants();
  }

  public static BusinessApplication createFromForm(ApplicationId applicationId, ApplicationSource source,
                                                    BusinessContext businessContext, OperatorInfo operatorInfo,
                                                    FileId jsonFileId, DefinitionVersion definitionVersion) {
    if (source == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("申请来源不能为空");
    }
    if (definitionVersion == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("流程定义版本不能为空");
    }
    BusinessApplication app = new BusinessApplication(applicationId, businessContext, operatorInfo, jsonFileId);
    app.formId = source.formId();
    app.batchId = source.batchId();
    app.definitionVersion = definitionVersion;
    app.status = ApplicationStatus.RUNNING;
    app.currentStep = ApplicationFlowStep.FORM_DETAIL_INGESTION;
    app.registerDomainEvent(ApplicationSpawnedEvent.of(app.id()));
    return app;
  }

  /**
   * 从数据库重建聚合根（全参工厂方法）。
   * <p>
   * 供 Repository 实现的 Converter 在 DO → 领域对象转换时调用，绕过业务校验直接装配所有字段。
   * 业务代码禁止使用本方法创建新对象，新建请用 {@link #createFromForm}。
   *
   * @return 装配完成的聚合根实例
   */
  public static BusinessApplication reconstitute(
    ApplicationId applicationId,
    String createdBy,
    String updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Version version,
    BatchId batchId,
    FormId formId,
    BusinessContext businessContext,
    OperatorInfo operatorInfo,
    FileId parsedJsonFileId,
    int expectedDetailCount,
    DefinitionVersion definitionVersion,
    ApplicationStatus status,
    ApplicationFlowStep currentStep,
    LocalDateTime applyTime,
    LocalDateTime completeTime,
    List<MaterialItem> planMaterials,
    BusinessFile packageFile) {
    BusinessApplication app = new BusinessApplication(applicationId, createdBy, updatedBy, createdAt, updatedAt, version);
    app.batchId = batchId;
    app.formId = formId;
    app.businessContext = businessContext;
    app.operatorInfo = operatorInfo;
    app.parsedJsonFileId = parsedJsonFileId;
    app.expectedDetailCount = expectedDetailCount;
    app.definitionVersion = definitionVersion;
    app.status = status;
    app.currentStep = currentStep;
    app.applyTime = applyTime;
    app.completeTime = completeTime;
    app.materialState = ApplicationMaterialState.of(planMaterials, packageFile);
    return app;
  }

  public FormId formId() {
    return this.formId;
  }

  public FileId parsedJsonFileId() {
    return this.parsedJsonFileId;
  }

  /**
   * 期望明细数量（只读）。
   *
   * @return 期望明细数量
   */
  public int expectedDetailCount() {
    return this.expectedDetailCount;
  }

  /**
   * 分配材料并智能合并历史附件（委托给可选材料能力，§6.2）。
   */
  public void assignMaterials(List<MaterialItem> requiredBlueprints) {
    this.materialState = this.materialState.assignMaterials(requiredBlueprints);
  }

  /** 逐个上传计划层材料。 */
  public void uploadIndividualPlanMaterial(String materialCode, BusinessFile file) {
    this.materialState = this.materialState.uploadIndividualPlanMaterial(materialCode, file, this.id());
  }

  /**
   * 移除某个材料项下指定的已上传文件
   *
   * @param materialCode 材料编码
   * @param fileId       文件 ID
   */
  public void removeIndividualPlanMaterial(String materialCode, FileId fileId) {
    this.materialState = this.materialState.removeIndividualPlanMaterial(materialCode, fileId, this.id());
  }

  /** 打包上传。 */
  public void uploadPackage(BusinessFile zipFile) {
    this.materialState = this.materialState.uploadPackage(zipFile);
  }

  /** 校验计划层材料是否满足要求。 */
  public boolean isPlanMaterialSatisfied(MaterialConditionContext context) {
    return this.materialState.isPlanMaterialSatisfied(context);
  }

  /** 供外部（应用层/明细层）判断是否处于打包模式。 */
  public boolean isPackageUploadMode() {
    return this.materialState.isPackageUploadMode();
  }

  public ApplicationFlowStep currentStep() {
    return this.currentStep;
  }

  /**
   * 完成当前步骤并跃迁到后继步骤。终态申请单不可继续推进；
   * 重复推进同一节点保持幂等。所有更新统一递增版本并记录操作人。
   */
  public void completeCurrentStep(ApplicationFlowStep nextStep) {
    if (nextStep == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("后继步骤不能为空");
    }
    // 幂等先于终态检查：终点步骤的自环推进不产生状态变化
    if (this.currentStep == nextStep) {
      return;
    }
    if (currentStatus() == null || currentStatus().isTerminal()) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
        .withLogDetail("终态申请单不可继续推进步骤");
    }

    ApplicationFlowStep previousStep = this.currentStep;
    this.currentStep = nextStep;
    if (nextStep == ApplicationFlowStep.COMPLETED) {
      transitStatus(ApplicationStatus.COMPLETED);
    } else {
      this.markUpdated(requireOperator());
    }
    this.registerDomainEvent(StepEnteredEvent.of(this.id(), previousStep, nextStep));
  }

  public void start() {
    transitStatus(ApplicationStatus.RUNNING);
  }

  public void waitForUser() {
    transitStatus(ApplicationStatus.WAITING_USER);
  }

  public void waitForExternal() {
    transitStatus(ApplicationStatus.WAITING_EXTERNAL);
  }

  public void fail() {
    transitStatus(ApplicationStatus.FAILED);
  }

  public void retry() {
    transitStatus(ApplicationStatus.RUNNING);
  }

  public void cancel() {
    transitStatus(ApplicationStatus.CANCELLED);
  }

  public void complete() {
    transitStatus(ApplicationStatus.COMPLETED);
  }

  private void transitStatus(ApplicationStatus target) {
    ApplicationStatus current = currentStatus();
    if (current == null || !current.canTransitTo(target)) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
        .withLogDetail("申请单状态不允许从 %s 迁移到 %s".formatted(current, target));
    }
    this.status = target;
    if (target == ApplicationStatus.COMPLETED) {
      this.completeTime = LocalDateTime.now();
    }
    this.markUpdated(requireOperator());
  }

  private ApplicationStatus currentStatus() {
    return this.status == null ? null : this.status.normalized();
  }

  private String requireOperator() {
    if (this.operatorInfo == null || this.operatorInfo.operatorId() == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("申请单缺少操作人信息，无法执行状态更新");
    }
    return this.operatorInfo.operatorId();
  }

  /**
   * 领域行为：审批驳回，将申请单状态置为 REJECTED。
   * <p>
   * 由审批结果事件监听器在收到 {@code ApprovalInstanceRejectedEventDTO} 后调用。
   * 状态一旦进入 REJECTED 即为终态，不可再流转。
   */
  public void reject() {
    terminateAs(ApplicationStatus.REJECTED);
  }

  /**
   * 领域行为：审批撤回，将申请单状态置为 WITHDRAWN。
   * <p>
   * 由审批结果事件监听器在收到 {@code ApprovalInstanceWithdrawnEventDTO} 后调用。
   * 状态一旦进入 WITHDRAWN 即为终态，不可再流转。
   */
  public void withdraw() {
    terminateAs(ApplicationStatus.WITHDRAWN);
  }

  /**
   * 审批拒绝与业务撤回属于 §8.1 注明的「明确终止行为」，不受一般迁移表约束。
   * 过渡期外部协作尚未接线、等待态未建立；阶段 3 接入后统一为仅 WAITING_EXTERNAL 可进入。
   */
  private void terminateAs(ApplicationStatus terminal) {
    ApplicationStatus current = currentStatus();
    if (current == terminal) {
      return;
    }
    if (current == null || current.isTerminal()) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
        .withLogDetail("终态申请单不可再变更状态");
    }
    this.status = terminal;
    this.markUpdated(requireOperator());
  }

  /**
   * 领域行为：记录管道执行轨迹
   * 聚合根本身并不会把这些详细的日志保存在自己的成员变量里（太重了），
   * 而是通过领域事件派发出去，让日志/审计限界上下文去持久化。
   */
  public void recordPipelineExecution(String phase, PipelineExecutionResult result) {
    // 如果管道里没有任何动作执行，不需要发事件
    if (result == null || result.actionRecords().isEmpty()) {
      return;
    }

    // 聚合根自己产生并注册领域事件
    this.registerDomainEvent(PipelineExecutedEvent.of(this.id(), this.currentStep(), phase, result));
  }

  /**
   * 领域行为：获取用于底层解析的目标文件凭证。
   * (内部封装了状态校验规则，对外不暴露简单的 getter)
   */
  public FileId requireFileForParsing() {
    if (this.parsedJsonFileId == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("Application: %s 申请单尚未挂载文件，无法发起解析".formatted(this.id()));
    }
    return this.parsedJsonFileId;
  }

  public BusinessMetaContext buildConfigQueryContext() {
    return BusinessMetaContext.of(this.businessContext);
  }

  @Override
  protected void validateInvariants() {

  }

  /**
   * 公开操作人信息（只读）。
   * <p>
   * 供通知、审计等扩展动作获取操作人信息，避免反射。
   *
   * @return 操作人信息实例
   */
  public OperatorInfo operatorInfo() {
    return this.operatorInfo;
  }

  /**
   * 公开业务上下文（只读）。
   * <p>
   * 供客户画像查询等扩展动作获取客户/产品/方案维度信息，避免反射。
   *
   * @return 业务上下文实例
   */
  public BusinessContext businessContext() {
    return this.businessContext;
  }

  public BatchId batchId() {
    return batchId;
  }

  /**
   * 流程实例启动时固定的流程定义版本（只读）。
   * 定义升级不影响在途实例：加载定义时始终使用本版本。
   */
  public DefinitionVersion definitionVersion() {
    return definitionVersion;
  }

  /**
   * 获取申请单宏观状态(只读)。
   * <p>
   * 供 ApplicationConverter 转换 DTO 使用。
   *
   * @return 申请单状态
   */
  public ApplicationStatus status() {
    return currentStatus();
  }

  /**
   * 获取打包上传文件(只读)。
   * <p>
   * 供 ApplicationConverter 转换 DTO 使用。
   *
   * @return 打包文件,未打包上传时为 null
   */
  public BusinessFile packageFile() {
    return this.materialState.packageFile();
  }

  /**
   * 获取计划层材料列表(只读)。
   * <p>
   * 供 MaterialConverter 转换 DTO 与 MaterialAppService 查询使用。
   *
   * @return 计划层材料列表
   */
  public List<MaterialItem> planMaterials() {
    return this.materialState.planMaterials();
  }

  /** 可选材料能力状态（§6.2，批次 37a）。 */
  public ApplicationMaterialState materialState() {
    return this.materialState;
  }

  /**
   * 获取申请时间(只读)。
   *
   * @return 申请时间
   */
  public LocalDateTime applyTime() {
    return this.applyTime;
  }

  /**
   * 获取完成时间(只读)。
   *
   * @return 完成时间
   */
  public LocalDateTime completeTime() {
    return this.completeTime;
  }
}
