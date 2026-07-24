package com.example.file.domain.model.aggregate.root;

import com.example.file.domain.model.enums.ErrorPolicy;
import com.example.file.domain.model.enums.TaskStatus;
import com.example.file.domain.model.valueobject.SubTaskSummary;
import com.example.file.domain.model.valueobject.TaskError;
import com.example.file.types.BizType;
import com.example.file.types.FileTaskId;
import com.example.file.types.TemplateCode;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.UserNo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ParseTask extends AggregateRoot<FileTaskId> {

  private BizType bizType;
  private TemplateCode templateCode;
  private String sourceFileName;
  private FileId sourceFileId;
  private TaskStatus status;
  private ErrorPolicy errorPolicy;
  private List<String> splitKeys;
  private int totalRows;
  private int subTaskCount;
  private int validCount;
  private int invalidCount;
  private List<SubTaskSummary> subTaskSummaries = new ArrayList<>();
  private List<TaskError> errors = new ArrayList<>();
  private LocalDateTime startedAt;
  private LocalDateTime finishedAt;

  // 业务创建
  private ParseTask(FileTaskId id, BizType bizType, String sourceFileName, FileId sourceFileId,
                    ErrorPolicy errorPolicy, List<String> splitKeys, UserNo userNo) {
    super(id, userNo);
    this.bizType = bizType;
    this.sourceFileName = sourceFileName;
    this.sourceFileId = sourceFileId;
    this.errorPolicy = errorPolicy;
    this.splitKeys = List.copyOf(splitKeys);
    this.status = TaskStatus.PENDING;
    this.startedAt = LocalDateTime.now();
    this.validateInvariants();
  }

  // 数据库重建
  public ParseTask(FileTaskId id, BizType bizType, TemplateCode templateCode, String sourceFileName,
                   FileId sourceFileId, TaskStatus status, ErrorPolicy errorPolicy, List<String> splitKeys,
                   int totalRows, int subTaskCount, int validCount, int invalidCount,
                   List<SubTaskSummary> subTaskSummaries, List<TaskError> errors,
                   LocalDateTime startedAt, LocalDateTime finishedAt,
                   UserNo createdBy, UserNo updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.bizType = bizType;
    this.templateCode = templateCode;
    this.sourceFileName = sourceFileName;
    this.sourceFileId = sourceFileId;
    this.status = status;
    this.errorPolicy = errorPolicy;
    this.splitKeys = splitKeys;
    this.totalRows = totalRows;
    this.subTaskCount = subTaskCount;
    this.validCount = validCount;
    this.invalidCount = invalidCount;
    this.subTaskSummaries = new ArrayList<>(subTaskSummaries);
    this.errors = new ArrayList<>(errors);
    this.startedAt = startedAt;
    this.finishedAt = finishedAt;
    this.validateInvariants();
  }

  public static ParseTask create(FileTaskId id, BizType bizType, String sourceFileName,
                                 FileId sourceFileId, ErrorPolicy errorPolicy,
                                 List<String> splitKeys, UserNo userNo) {
    if (bizType == null) throw new IllegalArgumentException("bizType null");
    if (errorPolicy == null) throw new IllegalArgumentException("errorPolicy null");
    if (sourceFileName == null || sourceFileName.isBlank()) throw new IllegalArgumentException("sourceFileName empty");
    return new ParseTask(id, bizType, sourceFileName, sourceFileId, errorPolicy, splitKeys, userNo);
  }

  public void markParsing() { this.status = TaskStatus.PARSING; }
  public void markSplitting() { this.status = TaskStatus.SPLITTING; }
  public void markValidating() { this.status = TaskStatus.VALIDATING; }

  public void bindTemplate(TemplateCode code) {
    this.templateCode = code;
  }

  public void recordSubTask(SubTaskSummary summary) {
    if (summary == null) throw new IllegalArgumentException("summary null");
    this.subTaskSummaries.add(summary);
    this.subTaskCount++;
    this.totalRows += summary.totalRows();
    this.validCount += summary.validRows();
    this.invalidCount += summary.invalidRows();
  }

  public void markSuccess() {
    if (subTaskSummaries.isEmpty()) throw new IllegalStateException("no subtasks recorded");
    this.status = TaskStatus.SUCCESS;
    this.finishedAt = LocalDateTime.now();
  }

  public void markPartialSuccess(int failedCount) {
    this.status = TaskStatus.PARTIAL_SUCCESS;
    this.invalidCount = failedCount;
    this.finishedAt = LocalDateTime.now();
  }

  public void markFailed(TaskError error) {
    this.status = TaskStatus.FAILED;
    if (error != null) this.errors.add(error);
    this.finishedAt = LocalDateTime.now();
  }

  @Override
  protected void validateInvariants() {
    if (bizType == null) throw new IllegalStateException("bizType null");
    if (status == null) throw new IllegalStateException("status null");
  }

  // Getters
  public BizType bizType() { return bizType; }
  public TemplateCode templateCode() { return templateCode; }
  public String sourceFileName() { return sourceFileName; }
  public FileId sourceFileId() { return sourceFileId; }
  public TaskStatus status() { return status; }
  public ErrorPolicy errorPolicy() { return errorPolicy; }
  public List<String> splitKeys() { return splitKeys; }
  public int totalRows() { return totalRows; }
  public int subTaskCount() { return subTaskCount; }
  public int validCount() { return validCount; }
  public int invalidCount() { return invalidCount; }
  public List<SubTaskSummary> subTaskSummaries() { return List.copyOf(subTaskSummaries); }
  public List<TaskError> errors() { return List.copyOf(errors); }
  public LocalDateTime startedAt() { return startedAt; }
  public LocalDateTime finishedAt() { return finishedAt; }
}
