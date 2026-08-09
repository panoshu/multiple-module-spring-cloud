package com.example.core.infrastructure.business.converter;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.BusinessExtension;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.business.aggregate.valueobject.enums.status.ApplicationStatus;
import com.example.core.domain.business.aggregate.valueobject.reference.PlanBizApplicationRef;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.core.infrastructure.business.entity.BusinessApplicationDO;
import com.example.core.infrastructure.engine.errorcode.CoreInfraErrorCode;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.SystemException;
import com.example.shared.identifier.id.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/**
 * 业务申请单 DO ↔ 领域对象转换器
 * <p>
 * 使用 kernel 聚合根的公开访问器与 {@link BusinessApplication#reconstitute} 工厂方法，
 * 彻底消除反射访问。{@link BusinessExtension} 通过注入的 {@link ObjectMapper} 多态序列化持久化为 JSON。
 * <p>
 * {@code planMaterials}/{@code packageFile} 为流程内内存态字段，不持久化到 DO。
 *
 * @author core-kernel
 * @since 2026/8/8
 */
@Mapper(componentModel = "spring")
public abstract class BusinessApplicationConverter {

  @Autowired
  protected ObjectMapper objectMapper;

  /**
   * 领域对象 → DO
   */
  public BusinessApplicationDO toDO(BusinessApplication app) {
    if (app == null) {
      return null;
    }
    BusinessApplicationDO aDo = new BusinessApplicationDO();
    aDo.setId(app.id() != null ? app.id().value() : null);

    aDo.setBatchId(app.batchId() != null ? app.batchId().value() : null);
    aDo.setFormId(app.formId() != null ? app.formId().value() : null);

    BusinessContext ctx = app.businessContext();
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

    OperatorInfo op = app.operatorInfo();
    if (op != null) {
      aDo.setChannel(op.channel() != null ? op.channel().name() : null);
      aDo.setOperatorId(op.operatorId() != null ? op.operatorId().value() : null);
      aDo.setOperatorName(op.operatorName());
      aDo.setIsProxy(op.isProxy());
    }

    aDo.setParsedJsonFileId(app.parsedJsonFileId() != null ? app.parsedJsonFileId().value() : null);
    aDo.setExpectedDetailCount(app.expectedDetailCount());

    aDo.setBusinessExtension(extensionToJson(app.businessExtension()));

    aDo.setStatus(app.status() != null ? app.status().name() : null);
    aDo.setCurrentStep(app.currentStep() != null ? app.currentStep().name() : null);
    aDo.setApplyTime(app.applyTime());
    aDo.setCompleteTime(app.completeTime());

    aDo.setCreatedBy(app.createdBy() != null ? app.createdBy().value() : null);
    aDo.setUpdatedBy(app.updatedBy() != null ? app.updatedBy().value() : null);
    aDo.setCreateTime(app.createdAt());
    aDo.setUpdateTime(app.updatedAt());
    aDo.setDeleted(false);
    aDo.setVersion(app.version() != null ? (int) app.version().value() : 0);
    return aDo;
  }

  /**
   * DO → 领域对象
   * <p>
   * 调用 {@link BusinessApplication#reconstitute} 工厂方法重建聚合根，绕过业务校验。
   * {@code planMaterials}/{@code packageFile} 为内存态字段，重建时置空。
   */
  public BusinessApplication toDomain(BusinessApplicationDO aDo) {
    if (aDo == null) {
      return null;
    }

    BusinessContext businessContext = buildBusinessContext(aDo);
    OperatorInfo operatorInfo = buildOperatorInfo(aDo);
    BusinessExtension extension = jsonToExtension(aDo.getBusinessExtension());

    return BusinessApplication.reconstitute(
      new ApplicationId(aDo.getId()),
      aDo.getCreatedBy() != null ? UserNo.of(aDo.getCreatedBy()) : null,
      aDo.getUpdatedBy() != null ? UserNo.of(aDo.getUpdatedBy()) : null,
      aDo.getCreateTime(),
      aDo.getUpdateTime(),
      aDo.getVersion() != null ? Version.of(aDo.getVersion().longValue()) : Version.initial(),
      aDo.getBatchId() != null ? BatchId.of(aDo.getBatchId()) : null,
      aDo.getFormId() != null ? new FormId(aDo.getFormId()) : null,
      businessContext,
      operatorInfo,
      extension,
      aDo.getParsedJsonFileId() != null ? new FileId(aDo.getParsedJsonFileId()) : null,
      aDo.getExpectedDetailCount() != null ? aDo.getExpectedDetailCount() : 0,
      aDo.getStatus() != null ? ApplicationStatus.valueOf(aDo.getStatus()) : null,
      aDo.getCurrentStep() != null ? ApplicationFlowStep.valueOf(aDo.getCurrentStep()) : null,
      aDo.getApplyTime(),
      aDo.getCompleteTime(),
      List.of(),
      null
    );
  }

  // ====================================================
  // 私有辅助方法
  // ====================================================

  private BusinessContext buildBusinessContext(BusinessApplicationDO aDo) {
    return new BusinessContext(
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
  }

  private OperatorInfo buildOperatorInfo(BusinessApplicationDO aDo) {
    return new OperatorInfo(
      aDo.getChannel() != null ? AnnuityChannel.valueOf(aDo.getChannel()) : null,
      aDo.getOperatorId() != null ? UserNo.of(aDo.getOperatorId()) : null,
      aDo.getOperatorName(),
      aDo.getIsProxy() != null && aDo.getIsProxy()
    );
  }

  private String extensionToJson(BusinessExtension extension) {
    if (extension == null) {
      return null;
    }
    try {
      return objectMapper.writeValueAsString(extension);
    } catch (JsonProcessingException e) {
      throw new SystemException(CoreInfraErrorCode.EXTENSION_SERIALIZATION_FAILED)
        .withLogDetail("序列化 BusinessExtension 失败: " + e.getMessage());
    }
  }

  private BusinessExtension jsonToExtension(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(json, BusinessExtension.class);
    } catch (JsonProcessingException e) {
      throw new SystemException(CoreInfraErrorCode.EXTENSION_DESERIALIZATION_FAILED)
        .withLogDetail("反序列化 BusinessExtension 失败: " + e.getMessage());
    }
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

  /**
   * 占位：申请单引用列表目前不持久化到本 DO。
   * 保留方法签名供未来扩展使用。
   */
  @SuppressWarnings("unused")
  private List<PlanBizApplicationRef> emptyApplicationRefs() {
    return List.of();
  }
}
