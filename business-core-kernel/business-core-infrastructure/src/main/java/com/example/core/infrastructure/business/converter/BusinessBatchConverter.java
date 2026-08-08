package com.example.core.infrastructure.business.converter;

import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.core.domain.business.aggregate.valueobject.reference.BusinessFormRef;
import com.example.core.infrastructure.business.entity.BusinessBatchDO;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.*;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 业务批次 DO ↔ 领域对象转换器
 * <p>
 * 使用 kernel 聚合根的公开访问器与 {@link BusinessBatch#reconstitute} 工厂方法，
 * 彻底消除反射访问。{@code businessFormRefs} 为内存态字段，重建时置空。
 *
 * @author core-kernel
 * @since 2026/8/8
 */
@Mapper(componentModel = "spring")
public abstract class BusinessBatchConverter {

  /**
   * 领域对象 → DO
   */
  public BusinessBatchDO toDO(BusinessBatch batch) {
    if (batch == null) {
      return null;
    }
    BusinessBatchDO aDo = new BusinessBatchDO();
    aDo.setId(batch.id() != null ? batch.id().value() : null);

    BusinessContext ctx = batch.businessContext();
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

    OperatorInfo op = batch.operatorInfo();
    if (op != null) {
      aDo.setChannel(op.channel() != null ? op.channel().name() : null);
      aDo.setOperatorId(op.operatorId() != null ? op.operatorId().value() : null);
      aDo.setOperatorName(op.operatorName());
      aDo.setIsProxy(op.isProxy());
    }

    aDo.setStatus(batch.status() != null ? batch.status().name() : null);
    aDo.setTotalApplicationCount(batch.totalApplicationCount());
    aDo.setSuccessCount(batch.successCount());
    aDo.setFailedCount(batch.failedCount());

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
   * <p>
   * 调用 {@link BusinessBatch#reconstitute} 工厂方法重建聚合根。
   * {@code businessFormRefs} 为内存态字段，重建时置空。
   */
  public BusinessBatch toDomain(BusinessBatchDO aDo) {
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

    List<BusinessFormRef> formRefs = List.of();

    return BusinessBatch.reconstitute(
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
      aDo.getFailedCount() != null ? aDo.getFailedCount() : 0,
      formRefs
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
