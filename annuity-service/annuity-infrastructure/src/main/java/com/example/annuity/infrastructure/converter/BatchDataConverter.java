package com.example.annuity.infrastructure.converter;

import com.example.annuity.infrastructure.entity.BatchDO;
import com.example.core.domain.aggregate.root.BusinessBatch;
import com.example.core.domain.aggregate.valueobject.BusinessContext;
import com.example.core.domain.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.aggregate.valueobject.enums.status.BatchStatus;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.UserNo;
import org.mapstruct.Mapper;

/**
 * 年金业务批次 DO ↔ 领域对象转换器
 * <p>
 * 通过 {@link KernelAggregateReflector} 反射访问 kernel 聚合根的私有字段。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Mapper(componentModel = "spring")
public interface BatchDataConverter {

  /**
   * 领域对象 → DO
   */
  default BatchDO toDO(BusinessBatch batch) {
    if (batch == null) {
      return null;
    }
    BatchDO aDo = new BatchDO();
    aDo.setId(KernelAggregateReflector.nullableId(batch.id()));

    BusinessContext ctx = KernelAggregateReflector.readBusinessContext(batch);
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

    OperatorInfo op = KernelAggregateReflector.readOperatorInfo(batch);
    if (op != null) {
      aDo.setChannel(KernelAggregateReflector.enumName(op.channel()));
      aDo.setOperatorId(KernelAggregateReflector.nullableId(op.operatorId()));
      aDo.setOperatorName(op.operatorName());
      aDo.setIsProxy(op.isProxy());
    }

    aDo.setStatus(KernelAggregateReflector.enumName(KernelAggregateReflector.readStatus(batch)));
    aDo.setTotalApplicationCount(KernelAggregateReflector.readTotalApplicationCount(batch));
    aDo.setSuccessCount(KernelAggregateReflector.readSuccessCount(batch));
    aDo.setFailedCount(KernelAggregateReflector.readFailedCount(batch));

    aDo.setCreatedBy(batch.createdBy() != null ? batch.createdBy().value() : null);
    aDo.setUpdatedBy(batch.updatedBy() != null ? batch.updatedBy().value() : null);
    aDo.setCreateTime(batch.createdAt());
    aDo.setUpdateTime(batch.updatedAt());
    aDo.setDeleted(false);
    aDo.setVersion(batch.version() != null ? (int) batch.version().value() : 0);
    return aDo;
  }

  /**
   * DO → 领域对象
   */
  default BusinessBatch toDomain(BatchDO aDo) {
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

    return KernelAggregateReflector.reconstituteBatch(
        BatchId.of(aDo.getId()),
        aDo.getCreatedBy() != null ? UserNo.of(aDo.getCreatedBy()) : null,
        aDo.getUpdatedBy() != null ? UserNo.of(aDo.getUpdatedBy()) : null,
        aDo.getCreateTime(),
        aDo.getUpdateTime(),
        aDo.getVersion() != null ? Version.of(aDo.getVersion().longValue()) : Version.initial(),
        businessContext,
        operatorInfo,
        aDo.getStatus() != null ? BatchStatus.valueOf(aDo.getStatus()) : null,
        aDo.getTotalApplicationCount() != null ? aDo.getTotalApplicationCount() : 0,
        aDo.getSuccessCount() != null ? aDo.getSuccessCount() : 0,
        aDo.getFailedCount() != null ? aDo.getFailedCount() : 0
    );
  }
}
