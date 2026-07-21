package com.example.annuity.infrastructure.converter;

import com.example.annuity.domain.errorcode.AnnuityDomainErrorCode;
import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.annuity.infrastructure.entity.ApplicationDO;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessContext;
import com.example.core.domain.aggregate.valueobject.BusinessExtension;
import com.example.core.domain.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.aggregate.valueobject.enums.status.ApplicationStatus;
import com.example.core.domain.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.SystemException;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.BatchId;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.FormId;
import com.example.shared.primitives.identity.UserNo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;

/**
 * 年金业务申请单 DO ↔ 领域对象转换器
 * <p>
 * 通过 {@link KernelAggregateReflector} 反射访问 kernel 聚合根的私有字段；
 * {@link BusinessExtension} 通过 Jackson 多态序列化持久化为 JSON 字符串。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@Mapper(componentModel = "spring")
public interface ApplicationDataConverter {

  /**
   * 共享 ObjectMapper，由 {@link AnnuityJacksonConfiguration} 注册 BusinessExtension Mix-in
   * 后注入。此处使用静态实例避免每次转换开销，Mix-in 在初始化块中注册。
   */
  ObjectMapper OBJECT_MAPPER = AnnuityJacksonConfiguration.configure(new ObjectMapper());

  /**
   * 领域对象 → DO
   */
  default ApplicationDO toDO(BusinessApplication app) {
    if (app == null) {
      return null;
    }
    ApplicationDO aDo = new ApplicationDO();
    aDo.setId(KernelAggregateReflector.nullableId(app.id()));

    BatchId batchId = KernelAggregateReflector.readBatchId(app);
    aDo.setBatchId(batchId != null ? batchId.value() : null);

    FormId formId = KernelAggregateReflector.readFormId(app);
    aDo.setFormId(formId != null ? formId.value() : null);

    BusinessContext ctx = KernelAggregateReflector.readBusinessContext(app);
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

    OperatorInfo op = KernelAggregateReflector.readOperatorInfo(app);
    if (op != null) {
      aDo.setChannel(KernelAggregateReflector.enumName(op.channel()));
      aDo.setOperatorId(KernelAggregateReflector.nullableId(op.operatorId()));
      aDo.setOperatorName(op.operatorName());
      aDo.setIsProxy(op.isProxy());
    }

    FileId parsedJsonFileId = KernelAggregateReflector.readParsedJsonFileId(app);
    aDo.setParsedJsonFileId(parsedJsonFileId != null ? parsedJsonFileId.value() : null);
    aDo.setExpectedDetailCount(KernelAggregateReflector.readExpectedDetailCount(app));

    BusinessExtension ext = KernelAggregateReflector.readBusinessExtension(app);
    aDo.setBusinessExtension(extensionToJson(ext));

    aDo.setStatus(KernelAggregateReflector.enumName(KernelAggregateReflector.readStatus(app)));
    aDo.setCurrentStep(KernelAggregateReflector.enumName(KernelAggregateReflector.readCurrentStep(app)));
    aDo.setApplyTime(KernelAggregateReflector.readApplyTime(app));
    aDo.setCompleteTime(KernelAggregateReflector.readCompleteTime(app));

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
   */
  default BusinessApplication toDomain(ApplicationDO aDo) {
    if (aDo == null) {
      return null;
    }

    BusinessContext businessContext = buildBusinessContext(aDo);
    OperatorInfo operatorInfo = buildOperatorInfo(aDo);
    BusinessExtension extension = jsonToExtension(aDo.getBusinessExtension());

    return KernelAggregateReflector.reconstituteApplication(
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
        aDo.getCompleteTime()
    );
  }

  // ====================================================
  // 私有辅助方法
  // ====================================================

  private BusinessContext buildBusinessContext(ApplicationDO aDo) {
    return new BusinessContext(
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
  }

  private OperatorInfo buildOperatorInfo(ApplicationDO aDo) {
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
      return OBJECT_MAPPER.writeValueAsString(extension);
    } catch (JsonProcessingException e) {
      throw new SystemException(AnnuityDomainErrorCode.INVALID_EXTENSION_DATA)
          .withLogDetail("序列化 BusinessExtension 失败: " + e.getMessage());
    }
  }

  private BusinessExtension jsonToExtension(String json) {
    if (json == null || json.isBlank()) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readValue(json, BusinessExtension.class);
    } catch (JsonProcessingException e) {
      throw new SystemException(AnnuityDomainErrorCode.INVALID_EXTENSION_DATA)
          .withLogDetail("反序列化 BusinessExtension 失败: " + e.getMessage());
    }
  }
}
