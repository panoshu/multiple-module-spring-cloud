package com.example.annuity.infrastructure.converter;

import com.example.annuity.infrastructure.entity.FormDO;
import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.BusinessFile;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.business.aggregate.valueobject.enums.status.FormStatus;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.FormId;
import com.example.shared.primitives.identity.UserNo;
import org.mapstruct.Mapper;

/**
 * 年金业务表单 DO ↔ 领域对象转换器
 * <p>
 * 通过 {@link KernelAggregateReflector} 反射访问 kernel 聚合根的私有字段。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Mapper(componentModel = "spring")
public interface FormDataConverter {

  /**
   * 领域对象 → DO
   */
  default FormDO toDO(BusinessForm form) {
    if (form == null) {
      return null;
    }
    FormDO aDo = new FormDO();
    aDo.setId(KernelAggregateReflector.nullableId(form.id()));

    BatchId batchId = KernelAggregateReflector.readBatchId(form);
    aDo.setBatchId(batchId != null ? batchId.value() : null);

    BusinessContext ctx = KernelAggregateReflector.readBusinessContext(form);
    if (ctx != null) {
      aDo.setBusinessType(KernelAggregateReflector.enumName(ctx.businessType()));
      aDo.setCustomerNo(KernelAggregateReflector.nullableId(ctx.customerNo()));
      aDo.setCustomerName(ctx.customerName());
      aDo.setProductNo(KernelAggregateReflector.nullableId(ctx.productNo()));
      aDo.setProductName(ctx.productName());
      aDo.setPlanNo(KernelAggregateReflector.nullableId(ctx.planNo()));
      aDo.setPlanName(ctx.planName());
      aDo.setOperationModel(KernelAggregateReflector.enumName(ctx.operationModel()));
      aDo.setAccountManager(KernelAggregateReflector.accountManagerValue(ctx.accountManager()));
    }

    OperatorInfo op = KernelAggregateReflector.readOperatorInfo(form);
    if (op != null) {
      aDo.setChannel(KernelAggregateReflector.enumName(op.channel()));
      aDo.setOperatorId(KernelAggregateReflector.nullableId(op.operatorId()));
      aDo.setOperatorName(op.operatorName());
      aDo.setIsProxy(op.isProxy());
    }

    BusinessFile formFile = KernelAggregateReflector.readFormFile(form);
    if (formFile != null) {
      aDo.setFormFileId(formFile.fileId() != null ? formFile.fileId().value() : null);
      aDo.setFormFileName(formFile.fileName());
      aDo.setFormFileSize(formFile.fileSizeBytes());
    }

    aDo.setFormStatus(KernelAggregateReflector.enumName(KernelAggregateReflector.readFormStatus(form)));

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
   */
  default BusinessForm toDomain(FormDO aDo) {
    if (aDo == null) {
      return null;
    }

    BusinessContext businessContext = new BusinessContext(
        aDo.getBusinessType() != null ? BusinessType.valueOf(aDo.getBusinessType()) : null,
        aDo.getCustomerNo() != null ? com.example.shared.primitives.identity.CustomerNo.of(aDo.getCustomerNo()) : null,
        aDo.getCustomerName(),
        aDo.getProductNo() != null ? com.example.shared.primitives.identity.ProductNo.of(aDo.getProductNo()) : null,
        aDo.getProductName(),
        aDo.getPlanNo() != null ? com.example.shared.primitives.identity.PlanNo.of(aDo.getPlanNo()) : null,
        aDo.getPlanName(),
        aDo.getOperationModel() != null ? OperationModel.valueOf(aDo.getOperationModel()) : null,
        KernelAggregateReflector.parseAccountManager(aDo.getAccountManager())
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

    return KernelAggregateReflector.reconstituteForm(
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
        aDo.getFormStatus() != null ? FormStatus.valueOf(aDo.getFormStatus()) : null
    );
  }
}
