package com.example.core.domain.business.aggregate.root;

import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.BusinessFile;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.enums.status.FormStatus;
import com.example.core.domain.business.aggregate.valueobject.reference.PlanBizApplicationRef;
import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
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

  public BusinessFile getFormFile() {
    return this.formFile;
  }

  public BatchId getBatchId() {
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
  public FormStatus getFormStatus() {
    return this.formStatus;
  }

  public BusinessContext getBusinessContext() {
    return this.businessContext;
  }

  public OperatorInfo getOperatorInfo() {
    return this.operatorInfo;
  }

  public BusinessMetaContext buildConfigQueryContext() {
    return BusinessMetaContext.of(this.businessContext);
  }

  @Override
  protected void validateInvariants() {

  }
}
