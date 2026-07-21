package com.example.core.domain.aggregate.root;

import com.example.core.domain.aggregate.valueobject.BusinessContext;
import com.example.core.domain.aggregate.valueobject.BusinessFile;
import com.example.core.domain.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.aggregate.valueobject.reference.PlanBizApplicationRef;
import com.example.core.domain.aggregate.valueobject.enums.status.FormStatus;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FormId;
import com.example.shared.primitives.identity.UserNo;
import com.example.shared.domain.aggregate.valueobject.Version;

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
  }

  protected BusinessForm(FormId formId, UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(formId, createdBy, updatedBy, createdAt, updatedAt, version);
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
