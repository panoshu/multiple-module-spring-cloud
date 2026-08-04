package com.example.annuity.infrastructure.converter;

import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.root.BusinessBatch;
import com.example.core.domain.business.aggregate.root.BusinessForm;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.BusinessExtension;
import com.example.core.domain.business.aggregate.valueobject.BusinessFile;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.enums.status.ApplicationStatus;
import com.example.core.domain.business.aggregate.valueobject.enums.status.BatchStatus;
import com.example.core.domain.business.aggregate.valueobject.enums.status.FormStatus;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.contract.Identifier;
import com.example.shared.identifier.id.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDateTime;

/**
 * Kernel 聚合根反射访问器
 * <p>
 * kernel 的 {@link BusinessApplication}、{@link BusinessBatch}、{@link BusinessForm} 聚合根
 * 字段多为 private/protected，且未提供公开的 reconstitute 工厂方法或 getter。本类封装反射逻辑，
 * 供 {@code ApplicationDataConverter}、{@code BatchDataConverter}、{@code FormDataConverter}
 * 在 DO ↔ 领域对象转换时使用。
 * <p>
 * <b>注意：</b>这是 kernel 未开放公开 API 前的临时方案，待 kernel 后续开放公开 accessor 后应移除反射。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
final class KernelAggregateReflector {

  private KernelAggregateReflector() {
  }

  // ====================================================
  // BusinessApplication 读取
  // ====================================================

  static BatchId readBatchId(BusinessApplication app) {
    return readField(app, "batchId", BatchId.class);
  }

  static FormId readFormId(BusinessApplication app) {
    return readField(app, "formId", FormId.class);
  }

  static BusinessContext readBusinessContext(BusinessApplication app) {
    return readField(app, "businessContext", BusinessContext.class);
  }

  static OperatorInfo readOperatorInfo(BusinessApplication app) {
    return readField(app, "operatorInfo", OperatorInfo.class);
  }

  static FileId readParsedJsonFileId(BusinessApplication app) {
    return readField(app, "parsedJsonFileId", FileId.class);
  }

  static int readExpectedDetailCount(BusinessApplication app) {
    Integer value = readField(app, "expectedDetailCount", Integer.class);
    return value != null ? value : 0;
  }

  static ApplicationStatus readStatus(BusinessApplication app) {
    return readField(app, "status", ApplicationStatus.class);
  }

  static ApplicationFlowStep readCurrentStep(BusinessApplication app) {
    return readField(app, "currentStep", ApplicationFlowStep.class);
  }

  static LocalDateTime readApplyTime(BusinessApplication app) {
    return readField(app, "applyTime", LocalDateTime.class);
  }

  static LocalDateTime readCompleteTime(BusinessApplication app) {
    return readField(app, "completeTime", LocalDateTime.class);
  }

  // ====================================================
  // BusinessApplication 重建
  // ====================================================

  /**
   * 通过反射调用 kernel 的 protected 全参构造器，并设置业务字段。
   */
  static BusinessApplication reconstituteApplication(
    ApplicationId id,
    UserNo createdBy,
    UserNo updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Version version,
    BatchId batchId,
    FormId formId,
    BusinessContext businessContext,
    OperatorInfo operatorInfo,
    BusinessExtension businessExtension,
    FileId parsedJsonFileId,
    int expectedDetailCount,
    ApplicationStatus status,
    ApplicationFlowStep currentStep,
    LocalDateTime applyTime,
    LocalDateTime completeTime) {
    try {
      Constructor<BusinessApplication> ctor = BusinessApplication.class.getDeclaredConstructor(
        ApplicationId.class, UserNo.class, UserNo.class,
        LocalDateTime.class, LocalDateTime.class, Version.class);
      ctor.setAccessible(true);
      BusinessApplication app = ctor.newInstance(id, createdBy, updatedBy, createdAt, updatedAt, version);

      setField(app, "batchId", batchId);
      setField(app, "formId", formId);
      setField(app, "businessContext", businessContext);
      setField(app, "operatorInfo", operatorInfo);
      setField(app, "businessExtension", businessExtension);
      setField(app, "parsedJsonFileId", parsedJsonFileId);
      setField(app, "expectedDetailCount", expectedDetailCount);
      setField(app, "status", status);
      setField(app, "currentStep", currentStep);
      setField(app, "applyTime", applyTime);
      setField(app, "completeTime", completeTime);
      return app;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("重建 BusinessApplication 失败: " + id, e);
    }
  }

  // ====================================================
  // BusinessBatch 读取
  // ====================================================

  static BusinessContext readBusinessContext(BusinessBatch batch) {
    return readField(batch, "businessContext", BusinessContext.class);
  }

  static OperatorInfo readOperatorInfo(BusinessBatch batch) {
    return readField(batch, "operatorInfo", OperatorInfo.class);
  }

  static BatchStatus readStatus(BusinessBatch batch) {
    return readField(batch, "status", BatchStatus.class);
  }

  static int readTotalApplicationCount(BusinessBatch batch) {
    Integer value = readField(batch, "totalApplicationCount", Integer.class);
    return value != null ? value : 0;
  }

  static int readSuccessCount(BusinessBatch batch) {
    Integer value = readField(batch, "successCount", Integer.class);
    return value != null ? value : 0;
  }

  static int readFailedCount(BusinessBatch batch) {
    Integer value = readField(batch, "failedCount", Integer.class);
    return value != null ? value : 0;
  }

  // ====================================================
  // BusinessBatch 重建
  // ====================================================

  static BusinessBatch reconstituteBatch(
    BatchId id,
    UserNo createdBy,
    UserNo updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Version version,
    BusinessContext businessContext,
    OperatorInfo operatorInfo,
    BatchStatus status,
    int totalApplicationCount,
    int successCount,
    int failedCount) {
    try {
      Constructor<BusinessBatch> ctor = BusinessBatch.class.getDeclaredConstructor(
        BatchId.class, UserNo.class, UserNo.class,
        LocalDateTime.class, LocalDateTime.class, Version.class);
      ctor.setAccessible(true);
      BusinessBatch batch = ctor.newInstance(id, createdBy, updatedBy, createdAt, updatedAt, version);

      setField(batch, "businessContext", businessContext);
      setField(batch, "operatorInfo", operatorInfo);
      setField(batch, "status", status);
      setField(batch, "totalApplicationCount", totalApplicationCount);
      setField(batch, "successCount", successCount);
      setField(batch, "failedCount", failedCount);
      return batch;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("重建 BusinessBatch 失败: " + id, e);
    }
  }

  // ====================================================
  // BusinessForm 读取
  // ====================================================

  static BatchId readBatchId(BusinessForm form) {
    return readField(form, "batchId", BatchId.class);
  }

  static BusinessContext readBusinessContext(BusinessForm form) {
    return readField(form, "businessContext", BusinessContext.class);
  }

  static OperatorInfo readOperatorInfo(BusinessForm form) {
    return readField(form, "operatorInfo", OperatorInfo.class);
  }

  static BusinessFile readFormFile(BusinessForm form) {
    return readField(form, "formFile", BusinessFile.class);
  }

  static FormStatus readFormStatus(BusinessForm form) {
    return readField(form, "formStatus", FormStatus.class);
  }

  // ====================================================
  // BusinessForm 重建
  // ====================================================

  static BusinessForm reconstituteForm(
    FormId id,
    UserNo createdBy,
    UserNo updatedBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Version version,
    BatchId batchId,
    BusinessContext businessContext,
    OperatorInfo operatorInfo,
    BusinessFile formFile,
    FormStatus formStatus) {
    try {
      Constructor<BusinessForm> ctor = BusinessForm.class.getDeclaredConstructor(
        FormId.class, UserNo.class, UserNo.class,
        LocalDateTime.class, LocalDateTime.class, Version.class);
      ctor.setAccessible(true);
      BusinessForm form = ctor.newInstance(id, createdBy, updatedBy, createdAt, updatedAt, version);

      setField(form, "batchId", batchId);
      setField(form, "businessContext", businessContext);
      setField(form, "operatorInfo", operatorInfo);
      setField(form, "formFile", formFile);
      setField(form, "formStatus", formStatus);
      return form;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("重建 BusinessForm 失败: " + id, e);
    }
  }

  // ====================================================
  // 枚举与值对象工具方法
  // ====================================================

  static String enumName(Enum<?> e) {
    return e != null ? e.name() : null;
  }

  static <T> String nullableId(Identifier<T> id) {
    if (id == null) {
      return null;
    }
    T value = id.value();
    return value != null ? value.toString() : null;
  }

  static String accountManagerValue(AccountManager am) {
    return am != null ? am.getValue() : null;
  }

  static AccountManager parseAccountManager(String value) {
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

  // ====================================================
  // 反射基础设施
  // ====================================================

  private static <T> T readField(Object target, String fieldName, Class<T> expectedType) {
    try {
      Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      Object value = field.get(target);
      return value == null ? null : expectedType.cast(value);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      return null;
    }
  }

  private static void setField(Object target, String fieldName, Object value)
    throws NoSuchFieldException, IllegalAccessException {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
