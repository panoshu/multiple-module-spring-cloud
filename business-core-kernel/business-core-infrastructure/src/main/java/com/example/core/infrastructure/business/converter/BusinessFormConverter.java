package com.example.core.infrastructure.business.converter;

import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.BusinessFile;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.business.aggregate.valueobject.enums.status.FormStatus;
import com.example.core.domain.business.aggregate.valueobject.reference.PlanBizApplicationRef;
import com.example.core.infrastructure.business.entity.BusinessFormDO;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.*;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 业务表单 DO ↔ 领域对象转换器
 * <p>
 * 使用 kernel 聚合根的公开访问器与 {@link BusinessForm#reconstitute} 工厂方法，
 * 彻底消除反射访问。{@code applicationRefs} 为内存态字段，重建时置空。
 *
 * @author core-kernel
 * @since 2026/8/8
 */
@Mapper(componentModel = "spring")
public abstract class BusinessFormConverter {

  /**
   * 领域对象 → DO
   */
  public BusinessFormDO toDO(BusinessForm form) {
    if (form == null) {
      return null;
    }
    BusinessFormDO aDo = new BusinessFormDO();
    aDo.setId(form.id() != null ? form.id().value() : null);

    aDo.setBatchId(form.batchId() != null ? form.batchId().value() : null);

    BusinessContext ctx = form.businessContext();
    if (ctx != null) {
      aDo.setBusinessType(ctx.businessType() != null ? ctx.businessType().name() : null);
      aDo.setCustomerNo(ctx.customerNo() != null ? ctx.customerNo().value() : null);
      aDo.setCustomerName(ctx.customerName());
      aDo.setProductNo(ctx.productNo() != null ? ctx.productNo().value() : null);
      aDo.setProductName(ctx.productName());
      aDo.setPlanNo(ctx.planNo() != null ? ctx.planNo().value() : null);
      aDo.setPlanName(ctx.planName());
      aDo.setOperationModel(ctx.operationModel() != null ? ctx.operationModel().name() : null);
      aDo.setAccountManager(ctx.accountManager() != null ? ctx.accountManager().getValue() : null);
    }

    OperatorInfo op = form.operatorInfo();
    if (op != null) {
      aDo.setChannel(op.channel() != null ? op.channel().name() : null);
      aDo.setOperatorId(op.operatorId() != null ? op.operatorId().value() : null);
      aDo.setOperatorName(op.operatorName());
      aDo.setIsProxy(op.isProxy());
    }

    BusinessFile formFile = form.formFile();
    if (formFile != null) {
      aDo.setFormFileId(formFile.fileId() != null ? formFile.fileId().value() : null);
      aDo.setFormFileName(formFile.fileName());
      aDo.setFormFileSize(formFile.fileSizeBytes());
    }

    aDo.setFormStatus(form.formStatus() != null ? form.formStatus().name() : null);

    aDo.setCreatedBy(form.createdBy() != null ? form.createdBy().value() : null);
    aDo.setUpdatedBy(form.updatedBy() != null ? form.updatedBy().value() : null);
    aDo.setCreateTime(form.createdAt());
    aDo.setUpdateTime(form.updatedAt());
    aDo.setDeleted(false);
    aDo.setVersion(form.version() != null ? (int) form.version().value() : 0);
    return aDo;
  }

  /**
   * DO → 领域对象
   * <p>
   * 调用 {@link BusinessForm#reconstitute} 工厂方法重建聚合根。
   * {@code applicationRefs} 为内存态字段，重建时置空。
   */
  public BusinessForm toDomain(BusinessFormDO aDo) {
    if (aDo == null) {
      return null;
    }

    BusinessContext businessContext = new BusinessContext(
      aDo.getBusinessType() != null ? BusinessType.valueOf(aDo.getBusinessType()) : null,
      aDo.getCustomerNo() != null ? CustomerNo.of(aDo.getCustomerNo()) : null,
      aDo.getCustomerName(),
      aDo.getProductNo() != null ? ProductNo.of(aDo.getProductNo()) : null,
      aDo.getProductName(),
      aDo.getPlanNo() != null ? PlanNo.of(aDo.getPlanNo()) : null,
      aDo.getPlanName(),
      aDo.getOperationModel() != null ? OperationModel.valueOf(aDo.getOperationModel()) : null,
      parseAccountManager(aDo.getAccountManager())
    );

    OperatorInfo operatorInfo = new OperatorInfo(
      aDo.getChannel() != null ? AnnuityChannel.valueOf(aDo.getChannel()) : null,
      aDo.getOperatorId() != null ? UserNo.of(aDo.getOperatorId()) : null,
      aDo.getOperatorName(),
      aDo.getIsProxy() != null && aDo.getIsProxy()
    );

    BusinessFile formFile = null;
    if (aDo.getFormFileId() != null) {
      formFile = new BusinessFile(
        new FileId(aDo.getFormFileId()),
        aDo.getFormFileName(),
        null,
        aDo.getFormFileSize()
      );
    }

    List<PlanBizApplicationRef> applicationRefs = List.of();

    return BusinessForm.reconstitute(
      new FormId(aDo.getId()),
      aDo.getCreatedBy() != null ? UserNo.of(aDo.getCreatedBy()) : null,
      aDo.getUpdatedBy() != null ? UserNo.of(aDo.getUpdatedBy()) : null,
      aDo.getCreateTime(),
      aDo.getUpdateTime(),
      aDo.getVersion() != null ? Version.of(aDo.getVersion().longValue()) : Version.initial(),
      aDo.getBatchId() != null ? BatchId.of(aDo.getBatchId()) : null,
      businessContext,
      operatorInfo,
      formFile,
      aDo.getFormStatus() != null ? FormStatus.valueOf(aDo.getFormStatus()) : null,
      applicationRefs
    );
  }

  private AccountManager parseAccountManager(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    for (AccountManager am : AccountManager.values()) {
      if (am.getValue().equals(value) || am.name().equals(value)) {
        return am;
      }
    }
    return null;
  }
}
