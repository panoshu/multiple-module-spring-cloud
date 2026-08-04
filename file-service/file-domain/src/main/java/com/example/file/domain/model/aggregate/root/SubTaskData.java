package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.model.enums.SubTaskStatus;
import com.example.file.domain.model.valueobject.BusinessContext;
import com.example.file.domain.model.valueobject.SubTaskSummary;
import com.example.file.domain.model.valueobject.ValidationError;
import com.example.file.domain.model.valueobject.ValidationResult;
import com.example.file.types.BizType;
import com.example.file.types.FileTaskId;
import com.example.file.types.SubTaskId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.identifier.id.UserNo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SubTaskData extends AggregateRoot<SubTaskId> {

  private static final int DEFAULT_TTL_DAYS = 30;

  private final FileTaskId fileTaskId;
  private final BizType bizType;
  private final String splitKeyValue;
  private final BusinessContext context;
  private final Map<String, Object> properties;
  private final Map<String, List<Map<String, Object>>> tables;
  private final int rowCount;
  private final LocalDateTime expiresAt;
  private SubTaskStatus status;
  private List<ValidationError> validationErrors = new ArrayList<>();
  private LocalDateTime consumedAt;

  // 业务创建
  private SubTaskData(SubTaskId id, FileTaskId fileTaskId, BizType bizType, String splitKeyValue,
                      BusinessContext context, Map<String, Object> properties,
                      Map<String, List<Map<String, Object>>> tables, int rowCount, UserNo userNo,
                      LocalDateTime expiresAt) {
    super(id, userNo);
    this.fileTaskId = fileTaskId;
    this.bizType = bizType;
    this.splitKeyValue = splitKeyValue;
    this.context = context;
    this.properties = properties;
    this.tables = tables;
    this.rowCount = rowCount;
    this.status = SubTaskStatus.PENDING;
    this.expiresAt = expiresAt != null ? expiresAt : LocalDateTime.now().plusDays(DEFAULT_TTL_DAYS);
    this.validateInvariants();
  }

  // 数据库重建
  public SubTaskData(SubTaskId id, FileTaskId fileTaskId, BizType bizType, String splitKeyValue,
                     BusinessContext context, Map<String, Object> properties,
                     Map<String, List<Map<String, Object>>> tables, int rowCount, SubTaskStatus status,
                     List<ValidationError> validationErrors, LocalDateTime expiresAt, LocalDateTime consumedAt,
                     UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.fileTaskId = fileTaskId;
    this.bizType = bizType;
    this.splitKeyValue = splitKeyValue;
    this.context = context;
    this.properties = properties;
    this.tables = tables;
    this.rowCount = rowCount;
    this.status = status;
    this.validationErrors = new ArrayList<>(validationErrors);
    this.expiresAt = expiresAt;
    this.consumedAt = consumedAt;
    this.validateInvariants();
  }

  public static SubTaskData create(SubTaskId id, FileTaskId fileTaskId, BizType bizType,
                                   String splitKeyValue, BusinessContext context,
                                   Map<String, Object> properties,
                                   Map<String, List<Map<String, Object>>> tables, int rowCount,
                                   UserNo userNo, LocalDateTime expiresAt) {
    return new SubTaskData(id, fileTaskId, bizType, splitKeyValue, context, properties,
      tables, rowCount, userNo, expiresAt);
  }

  public void applyValidationResult(ValidationResult result) {
    this.validationErrors = new ArrayList<>(result.errors());
    this.status = result.isValid() ? SubTaskStatus.VALID : SubTaskStatus.INVALID;
  }

  public void markConsumed() {
    if (this.status != SubTaskStatus.VALID && this.status != SubTaskStatus.INVALID) {
      throw new IllegalStateException("Cannot consume subtask in status " + this.status);
    }
    this.status = SubTaskStatus.CONSUMED;
    this.consumedAt = LocalDateTime.now();
  }

  public boolean isExpired() {
    return LocalDateTime.now().isAfter(expiresAt);
  }

  @Override
  protected void validateInvariants() {
    if (fileTaskId == null) throw new IllegalStateException("fileTaskId null");
    if (bizType == null) throw new IllegalStateException("bizType null");
    if (status == null) throw new IllegalStateException("status null");
  }

  public SubTaskSummary toSummary() {
    int valid = (status == SubTaskStatus.VALID || status == SubTaskStatus.CONSUMED) ? rowCount : 0;
    int invalid = (status == SubTaskStatus.INVALID) ? rowCount : 0;
    return new SubTaskSummary(id(), splitKeyValue, rowCount, valid, invalid, status);
  }

  public FileTaskId fileTaskId() {
    return fileTaskId;
  }

  public BizType bizType() {
    return bizType;
  }

  public String splitKeyValue() {
    return splitKeyValue;
  }

  public BusinessContext context() {
    return context;
  }

  public Map<String, Object> properties() {
    return properties;
  }

  public Map<String, List<Map<String, Object>>> tables() {
    return tables;
  }

  public int rowCount() {
    return rowCount;
  }

  public SubTaskStatus status() {
    return status;
  }

  public List<ValidationError> validationErrors() {
    return List.copyOf(validationErrors);
  }

  public LocalDateTime expiresAt() {
    return expiresAt;
  }

  public LocalDateTime consumedAt() {
    return consumedAt;
  }
}
