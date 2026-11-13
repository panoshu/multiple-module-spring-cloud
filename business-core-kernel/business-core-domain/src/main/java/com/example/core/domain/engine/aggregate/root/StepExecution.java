package com.example.core.domain.engine.aggregate.root;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionState;
import com.example.core.types.ApplicationId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;

import java.time.LocalDateTime;

/**
 * 步骤执行聚合根（spec §7.2）。
 * <p>
 * 每次步骤尝试都有独立记录，不将完整执行历史塞入 {@code BusinessApplication}。
 * 唯一性语义：{@code (applicationId, stepCode, attempt)} 与 {@code requestNo} 唯一，
 * 同一流程同一时刻最多一个运行中执行——由持久化唯一索引与 Repository 查询共同保证。
 * <p>
 * 本批次为纯领域模型切片，不接线运行时、不注册事件、不改 DDL。
 */
public class StepExecution extends AggregateRoot<StepExecutionId> {

  /** checkpoint_ref 列宽（VARCHAR(255)），派发载荷必须适配，超出即拒绝。 */
  private static final int MAX_CHECKPOINT_LENGTH = 255;

  private ApplicationId applicationId;
  private DefinitionVersion definitionVersion;
  private StepCode stepCode;
  private int attempt;
  private String requestNo;
  private StepExecutionState state;
  private LocalDateTime startedAt;
  private LocalDateTime endedAt;
  private String errorCode;
  private String errorSummary;
  private String checkpointRef;

  protected StepExecution(StepExecutionId id, String createdBy) {
    super(id, createdBy);
    this.validateInvariants();
  }

  protected StepExecution(StepExecutionId id, String createdBy, String updatedBy,
                          LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.validateInvariants();
  }

  /**
   * 业务创建：登记一次新的步骤尝试，初始为 PENDING。
   */
  public static StepExecution create(StepExecutionId id, ApplicationId applicationId,
                                     DefinitionVersion definitionVersion, StepCode stepCode,
                                     int attempt, String requestNo, String createdBy) {
    if (id == null || applicationId == null || definitionVersion == null || stepCode == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("步骤执行创建参数不完整");
    }
    StepExecution execution = new StepExecution(id, createdBy);
    execution.applicationId = applicationId;
    execution.definitionVersion = definitionVersion;
    execution.stepCode = stepCode;
    execution.attempt = attempt;
    execution.requestNo = requestNo;
    execution.state = StepExecutionState.PENDING;
    execution.validateInvariants();
    return execution;
  }

  /**
   * 由已失败或待重试的执行生成下一次尝试：attempt 递增，沿用原 requestNo（§7.3）。
   * 历史记录不被覆盖。
   */
  public static StepExecution retryFrom(StepExecutionId newId, StepExecution previous, String operator) {
    if (previous == null || previous.state == null || previous.state.isTerminal()) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
        .withLogDetail("终态步骤执行不可作为重试来源");
    }
    StepExecution retry = new StepExecution(newId, operator);
    retry.applicationId = previous.applicationId;
    retry.definitionVersion = previous.definitionVersion;
    retry.stepCode = previous.stepCode;
    retry.attempt = previous.attempt + 1;
    retry.requestNo = previous.requestNo;
    retry.state = StepExecutionState.PENDING;
    retry.validateInvariants();
    return retry;
  }

  /**
   * 从数据库重建聚合根，绕过业务创建校验直接装配全部字段。
   */
  public static StepExecution reconstitute(
    StepExecutionId id, String createdBy, String updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt, Version version,
    ApplicationId applicationId, DefinitionVersion definitionVersion, StepCode stepCode,
    int attempt, String requestNo, StepExecutionState state,
    LocalDateTime startedAt, LocalDateTime endedAt,
    String errorCode, String errorSummary, String checkpointRef) {
    StepExecution execution = new StepExecution(id, createdBy, updatedBy, createdAt, updatedAt, version);
    execution.applicationId = applicationId;
    execution.definitionVersion = definitionVersion;
    execution.stepCode = stepCode;
    execution.attempt = attempt;
    execution.requestNo = requestNo;
    execution.state = state;
    execution.startedAt = startedAt;
    execution.endedAt = endedAt;
    execution.errorCode = errorCode;
    execution.errorSummary = errorSummary;
    execution.checkpointRef = checkpointRef;
    execution.validateInvariants();
    return execution;
  }

  public void start(String operator) {
    transitTo(StepExecutionState.RUNNING, operator);
  }

  public void waitForExternal(String operator) {
    transitTo(StepExecutionState.WAITING_EXTERNAL, operator);
  }

  public void succeed(String operator) {
    transitTo(StepExecutionState.SUCCEEDED, operator);
  }

  public void fail(String errorCode, String errorSummary, String operator) {
    this.errorCode = errorCode;
    this.errorSummary = errorSummary;
    transitTo(StepExecutionState.FAILED, operator);
  }

  public void retryWait(String operator) {
    transitTo(StepExecutionState.RETRY_WAIT, operator);
  }

  public void skip(String operator) {
    transitTo(StepExecutionState.SKIPPED, operator);
  }

  public void cancel(String operator) {
    transitTo(StepExecutionState.CANCELLED, operator);
  }

  public void retry(String operator) {
    transitTo(StepExecutionState.RUNNING, operator);
  }

  /**
   * 记录挂起点恢复载荷（spec §7.2 检查点 / §9.2 阶段 B 事务外派发的参数恢复依据）。
   * <p>
   * 载荷由应用层的紧凑 JSON 构成，写入前校验不超过 {@code checkpoint_ref} 列宽；
   * 校验失败先于任何字段变更，避免留下部分状态。允许传入 {@code null} 清除检查点。
   * 本方法不改变步骤执行状态，仅更新检查点与乐观锁版本。
   */
  public void recordDispatchPayload(String payload, String operator) {
    if (payload != null && payload.length() > MAX_CHECKPOINT_LENGTH) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("派发载荷超过 checkpoint_ref 列宽 %d：%d"
          .formatted(MAX_CHECKPOINT_LENGTH, payload.length()));
    }
    this.checkpointRef = payload;
    this.markUpdated(operator);
  }

  private void transitTo(StepExecutionState target, String operator) {
    if (this.state == null || !this.state.canTransitTo(target)) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
        .withLogDetail("步骤执行状态不允许从 %s 迁移到 %s".formatted(this.state, target));
    }
    this.state = target;
    if (target == StepExecutionState.RUNNING && this.startedAt == null) {
      this.startedAt = LocalDateTime.now();
    }
    if (target.isTerminal() || target == StepExecutionState.FAILED) {
      this.endedAt = LocalDateTime.now();
    }
    this.markUpdated(operator);
  }

  @Override
  protected void validateInvariants() {
    if (this.state == null) {
      return; // 构造阶段字段尚未装配
    }
    if (this.applicationId == null || this.definitionVersion == null || this.stepCode == null
      || this.requestNo == null || this.requestNo.isBlank() || this.attempt <= 0) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("步骤执行聚合不变量被破坏");
    }
  }

  public ApplicationId applicationId() {
    return applicationId;
  }

  public DefinitionVersion definitionVersion() {
    return definitionVersion;
  }

  public StepCode stepCode() {
    return stepCode;
  }

  public int attempt() {
    return attempt;
  }

  public String requestNo() {
    return requestNo;
  }

  public StepExecutionState state() {
    return state;
  }

  public LocalDateTime startedAt() {
    return startedAt;
  }

  public LocalDateTime endedAt() {
    return endedAt;
  }

  public String errorCode() {
    return errorCode;
  }

  public String errorSummary() {
    return errorSummary;
  }

  public String checkpointRef() {
    return checkpointRef;
  }
}
