package com.example.core.domain.business.aggregate.root;

import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.BusinessFile;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.enums.status.FormStatus;
import com.example.core.domain.business.aggregate.valueobject.reference.PlanBizApplicationRef;
import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.domain.business.event.FormUploadedEvent;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import com.example.shared.identifier.id.BatchId;
import com.example.shared.identifier.id.FormId;
import com.example.shared.identifier.id.UserNo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业务表单聚合根
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/12 15:21
 */

public class BusinessForm extends AggregateRoot<FormId> {
  BusinessFile formFile;
  FormStatus formStatus;
  List<PlanBizApplicationRef> applicationRefs;
  private BatchId batchId;
  private BusinessContext businessContext;
  private OperatorInfo operatorInfo;

  protected BusinessForm(FormId formId, UserNo userNo) {
    super(formId, userNo);
    this.validateInvariants();
  }

  protected BusinessForm(FormId formId, UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(formId, createdBy, updatedBy, createdAt, updatedAt, version);
    this.validateInvariants();
  }

  /**
   * 工厂方法：创建新业务表单。
   *
   * @param formId         表单ID
   * @param batchId        关联批次ID
   * @param businessContext 业务上下文
   * @param operatorInfo   操作人信息
   * @param formFile       表单文件
   * @return 新创建的表单聚合根
   */
  public static BusinessForm create(FormId formId, BatchId batchId, BusinessContext businessContext, OperatorInfo operatorInfo, BusinessFile formFile) {
    BusinessForm form = new BusinessForm(formId, operatorInfo.operatorId());
    form.batchId = batchId;
    form.businessContext = businessContext;
    form.operatorInfo = operatorInfo;
    form.formFile = formFile;
    form.formStatus = FormStatus.UPLOADED;
    form.registerDomainEvent(FormUploadedEvent.of(
      formId,
      formFile != null ? formFile.fileId() : null,
      formFile != null ? formFile.fileName() : null
    ));
    return form;
  }

  /**
   * 从数据库重建聚合根（全参工厂方法）。
   * <p>
   * 供 Repository 实现的 Converter 在 DO → 领域对象转换时调用，绕过业务校验直接装配所有字段。
   * 业务代码禁止使用本方法创建新对象，新建请用 {@link #create}。
   *
   * @return 装配完成的聚合根实例
   */
  public static BusinessForm reconstitute(
    FormId formId,
    UserNo createdBy,
    UserNo updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Version version,
    BatchId batchId,
    BusinessContext businessContext,
    OperatorInfo operatorInfo,
    BusinessFile formFile,
    FormStatus formStatus,
    List<PlanBizApplicationRef> applicationRefs) {
    BusinessForm form = new BusinessForm(formId, createdBy, updatedBy, createdAt, updatedAt, version);
    form.batchId = batchId;
    form.businessContext = businessContext;
    form.operatorInfo = operatorInfo;
    form.formFile = formFile;
    form.formStatus = formStatus;
    form.applicationRefs = applicationRefs;
    return form;
  }

  public BusinessFile formFile() {
    return this.formFile;
  }

  public BatchId batchId() {
    return this.batchId;
  }

  public void markAsUploaded(BusinessFile uploadedFile) {
    this.formFile = uploadedFile;
    // TODO 创建领域事件
  }

  public void markAsParsing() {
    this.formStatus = FormStatus.PARSING;
    // TODO 创建领域事件
  }

  public void markAsParsed() {
    this.formStatus = FormStatus.PARSED;
    // TODO 创建领域事件
  }

  /**
   * 行为:标记表单为已删除
   *
   * <p>只有 UPLOADED/PARSED/WAITING_UPLOAD 状态的表单才能删除。
   * 已是 DELETED 状态直接返回(幂等)。
   *
   * @throws DomainException 当表单状态不允许删除时
   */
  public void markAsDeleted() {
    if (this.formStatus == FormStatus.DELETED) {
      return;
    }
    if (this.formStatus != FormStatus.UPLOADED
      && this.formStatus != FormStatus.PARSED
      && this.formStatus != FormStatus.WAITING_UPLOAD) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
        .withLogDetail("只有已上传/已解析/待上传的表单才能删除, FormId: %s, status: %s"
          .formatted(this.id().value(), this.formStatus));
    }
    this.formStatus = FormStatus.DELETED;
  }

  /**
   * 获取表单状态
   */
  public FormStatus formStatus() {
    return this.formStatus;
  }

  public BusinessContext businessContext() {
    return this.businessContext;
  }

  public OperatorInfo operatorInfo() {
    return this.operatorInfo;
  }

  /**
   * 获取关联的申请单引用列表(只读)。
   *
   * @return 申请单引用列表
   */
  public List<PlanBizApplicationRef> applicationRefs() {
    return this.applicationRefs;
  }

  public BusinessMetaContext buildConfigQueryContext() {
    return BusinessMetaContext.of(this.businessContext);
  }

  @Override
  protected void validateInvariants() {

  }
}
