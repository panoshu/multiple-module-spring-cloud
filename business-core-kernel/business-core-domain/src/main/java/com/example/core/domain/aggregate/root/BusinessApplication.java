package com.example.core.domain.aggregate.root;

import com.example.core.domain.aggregate.valueobject.*;
import com.example.core.domain.errorcode.CoreDomainErrorCode;
import com.example.core.domain.event.ApplicationSpawnedEvent;
import com.example.core.domain.event.PipelineExecutedEvent;
import com.example.core.domain.event.StepEnteredEvent;
import com.example.core.domain.aggregate.valueobject.enums.status.ApplicationStatus;
import com.example.core.domain.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.exception.DomainException;
import com.example.shared.primitives.identity.*;
import com.example.shared.domain.aggregate.valueobject.Version;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

  private BusinessContext businessContext;
  private OperatorInfo operatorInfo;

  private BusinessExtension businessExtension;

  private FileId parsedJsonFileId;
  private int expectedDetailCount;

  // TODO 状态与流程状态处理
  private ApplicationStatus status; // 宏观状态
  private ApplicationFlowStep currentStep;

  private LocalDateTime applyTime;
  private LocalDateTime completeTime;

  private List<MaterialItem> planMaterials;
  private BusinessFile packageFile;

  private BusinessFile acceptanceFile;

  protected BusinessApplication(ApplicationId applicationId, UserNo userNo) {
    super(applicationId, userNo);
  }

  protected BusinessApplication(ApplicationId applicationId, UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(applicationId, createdBy, updatedBy, createdAt, updatedAt, version);
  }

  protected BusinessApplication(ApplicationId applicationId, BusinessContext businessContext, OperatorInfo operatorInfo, FileId jsonFileId) {
    super(applicationId, operatorInfo.operatorId());
    this.businessContext = businessContext;
    this.operatorInfo = operatorInfo;
    this.parsedJsonFileId = jsonFileId;
  }

  public static BusinessApplication createFromForm(ApplicationId applicationId, BusinessContext businessContext, OperatorInfo operatorInfo, FileId jsonFileId) {
    BusinessApplication app = new BusinessApplication(applicationId, businessContext, operatorInfo, jsonFileId);
    app.status = ApplicationStatus.PROCESSING;
    app.currentStep = ApplicationFlowStep.FORM_DETAIL_INGESTION;
    app.registerDomainEvent(ApplicationSpawnedEvent.of(app.id()));
    return app;
  }

  public FormId queryFormInfo() {
    return this.formId;
  }

  public FileId getParsedJsonFileId() {
    return this.parsedJsonFileId;
  }

  /**
   * 分配材料并智能合并历史附件 (适配 Immutable Record)
   */
  public void assignMaterials(List<MaterialItem> requiredBlueprints) {
    if (this.planMaterials == null || this.planMaterials.isEmpty()) {
      this.planMaterials = requiredBlueprints;
      return;
    }

    // 1. 提取历史上传记录，建立索引：materialCode -> UploadInfo
    Map<String, Optional<MaterialItem.UploadInfo>> historyUploads = this.planMaterials.stream()
      .collect(Collectors.toMap(
        MaterialItem::materialCode,
        MaterialItem::uploadInfo,
        // 如果有重复的code，保留现有的
        (existing, replacement) -> existing
      ));

    // 2. 遍历新的蓝图需求，如果是历史已经传过附件的，利用 withUpload / 构造器 还原状态
    this.planMaterials = requiredBlueprints.stream().map(blueprint -> {
      Optional<MaterialItem.UploadInfo> oldUpload = historyUploads.getOrDefault(blueprint.materialCode(), Optional.empty());

      if (oldUpload.isPresent()) {
        // 因为是 record，我们直接通过全参构造器，把老文件的 uploadInfo 塞给新蓝图
        return new MaterialItem(
          blueprint.materialCode(),
          blueprint.materialName(),
          blueprint.level(),
          blueprint.requirement(),
          blueprint.conditionRule(),
          oldUpload
        );
      }
      return blueprint;
    }).toList();
  }

  /**
   * 逐个上传计划层材料
   */
  public void uploadIndividualPlanMaterial(String materialCode, BusinessFile file) {
    // 如果已经打包上传了，不允许再逐个传
    if (this.packageFile != null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_OPERATION)
        .withLogDetail("Application: %s 已使用打包上传模式(%s)，不可逐个上传计划层材料".formatted(this.id(), this.packageFile.fileName()));
    }
    this.planMaterials = this.planMaterials.stream()
      .map(m -> m.materialCode().equals(materialCode) ? m.withUpload(file) : m)
      .toList();
  }

  /**
   * 打包上传
   */
  public void uploadPackage(BusinessFile zipFile) {
    // 事实发生：记录压缩包ID
    this.packageFile = zipFile;

    // 一旦打包上传，清空之前逐个上传的文件记录
    this.planMaterials = this.planMaterials.stream()
      .map(m -> new MaterialItem(
        m.materialCode(), m.materialName(), m.level(),
        m.requirement(), m.conditionRule(),
        Optional.empty() // 清空已有的上传记录
      ))
      .toList();
  }

  /**
   * 校验计划层材料是否满足要求
   */
  public boolean isPlanMaterialSatisfied(MaterialConditionContext context) {
    // 1. 只要存在打包文件，计划层直接放行
    if (this.packageFile != null) {
      return true;
    }

    // 2. 逐个上传模式：流式校验每一个 MaterialItem 内聚的规则
    return this.planMaterials.stream()
      .allMatch(m -> m.isSatisfied(context));
  }

  /**
   * 提供查询方法，供外部（应用层/明细层）判断是否处于打包模式
   */
  public boolean isPackageUploadMode() {
    return this.packageFile != null;
  }

  public ApplicationFlowStep currentStep() {
    return this.currentStep;
  }

  public void transit(ApplicationFlowStep nextStep) {
    // TODO 增加状态控制?
    if (this.currentStep == nextStep) {
      return;
    }

    ApplicationFlowStep previousStep = this.currentStep;
    this.currentStep = nextStep;
    if (nextStep == ApplicationFlowStep.COMPLETED) {
      this.status = ApplicationStatus.COMPLETED;
    }
    this.registerDomainEvent(StepEnteredEvent.of(this.id(), previousStep, nextStep));
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

  /**
   * 领域行为：获取绑定的表单 ID (ID 是标识，提供只读获取是合理的)
   */
  public FormId bindedFormId() {
    return this.formId;
  }

  public BusinessMetaContext buildConfigQueryContext() {
    return BusinessMetaContext.of(this.businessContext);
  }

  @Override
  protected void validateInvariants() {

  }

  public BatchId getBatchId() {
    return batchId;
  }
}
