package com.example.core.domain.engine.aggregate.root;

import com.example.core.domain.business.errorcode.CoreDomainErrorCode;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.InteractionType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.ExternalInteractionStatus;
import com.example.core.domain.engine.event.ExternalInteractionRequestedEvent;
import com.example.core.types.ApplicationId;
import com.example.core.types.ExternalInteractionId;
import com.example.core.types.StepExecutionId;
import com.example.shared.domain.aggregate.root.AggregateRoot;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;

import java.time.LocalDateTime;

/**
 * 外部协作聚合根（spec §7.3）。
 * <p>
 * 统一管理文件解析、出站文件生成与审批三类外部交互。{@code requestNo} 默认由
 * {@code stepExecutionId} 派生且重试时沿用，保证同一外部请求不被重复创建；
 * 事件关联优先按 requestNo、其次按 externalTaskId，缺少任一关联信息不得盲目推进。
 * <p>
 * 创建时注册 {@link ExternalInteractionRequestedEvent}，由 Repository 经 EventBus 发布（§13.2），
 * 供 AFTER_COMMIT 尽快认领与恢复任务兜底；本类不触发外部调用。
 */
public class ExternalInteraction extends AggregateRoot<ExternalInteractionId> {

  private ApplicationId applicationId;
  private StepExecutionId stepExecutionId;
  private InteractionType type;
  private String requestNo;
  private String externalTaskId;
  private ExternalInteractionStatus status;
  private String resultRef;
  private int retryCount;
  private LocalDateTime nextRetryAt;
  private String lastError;
  private LocalDateTime completedAt;

  protected ExternalInteraction(ExternalInteractionId id, String createdBy) {
    super(id, createdBy);
    this.validateInvariants();
  }

  protected ExternalInteraction(ExternalInteractionId id, String createdBy, String updatedBy,
                                LocalDateTime createdAt, LocalDateTime updatedAt, Version version) {
    super(id, createdBy, updatedBy, createdAt, updatedAt, version);
    this.validateInvariants();
  }

  /**
   * 业务创建：登记一次待派发的外部协作。requestNo 由步骤执行 ID 稳定派生，不接受外部传入。
   */
  public static ExternalInteraction create(ExternalInteractionId id, ApplicationId applicationId,
                                           StepExecutionId stepExecutionId, InteractionType type,
                                           String createdBy) {
    if (id == null || applicationId == null || stepExecutionId == null || type == null) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("外部协作创建参数不完整");
    }
    ExternalInteraction interaction = new ExternalInteraction(id, createdBy);
    interaction.applicationId = applicationId;
    interaction.stepExecutionId = stepExecutionId;
    interaction.type = type;
    interaction.requestNo = deriveRequestNo(stepExecutionId);
    interaction.status = ExternalInteractionStatus.PENDING_DISPATCH;
    interaction.validateInvariants();
    interaction.registerDomainEvent(
      ExternalInteractionRequestedEvent.of(id, applicationId, interaction.requestNo));
    return interaction;
  }

  private static String deriveRequestNo(StepExecutionId stepExecutionId) {
    return "SE-" + stepExecutionId.value();
  }

  /**
   * 从数据库重建聚合根，绕过业务创建校验直接装配全部字段。
   */
  public static ExternalInteraction reconstitute(
    ExternalInteractionId id, String createdBy, String updatedBy,
    LocalDateTime createdAt, LocalDateTime updatedAt, Version version,
    ApplicationId applicationId, StepExecutionId stepExecutionId, InteractionType type,
    String requestNo, String externalTaskId, ExternalInteractionStatus status,
    String resultRef, int retryCount, LocalDateTime nextRetryAt,
    String lastError, LocalDateTime completedAt) {
    ExternalInteraction interaction =
      new ExternalInteraction(id, createdBy, updatedBy, createdAt, updatedAt, version);
    interaction.applicationId = applicationId;
    interaction.stepExecutionId = stepExecutionId;
    interaction.type = type;
    interaction.requestNo = requestNo;
    interaction.externalTaskId = externalTaskId;
    interaction.status = status;
    interaction.resultRef = resultRef;
    interaction.retryCount = retryCount;
    interaction.nextRetryAt = nextRetryAt;
    interaction.lastError = lastError;
    interaction.completedAt = completedAt;
    interaction.validateInvariants();
    return interaction;
  }

  public void markDispatching(String operator) {
    transitTo(ExternalInteractionStatus.DISPATCHING, operator);
  }

  public void markWaitingCallback(String externalTaskId, String operator) {
    if (!hasText(externalTaskId)) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("等待回调时外部任务 ID 不可为空");
    }
    transitTo(ExternalInteractionStatus.WAITING_CALLBACK, operator);
    this.externalTaskId = externalTaskId;
  }

  public void succeed(String resultRef, String operator) {
    transitTo(ExternalInteractionStatus.SUCCEEDED, operator);
    this.resultRef = resultRef;
  }

  public void fail(String lastError, String operator) {
    transitTo(ExternalInteractionStatus.FAILED, operator);
    this.lastError = lastError;
  }

  public void markUnknown(String lastError, String operator) {
    transitTo(ExternalInteractionStatus.UNKNOWN, operator);
    this.lastError = lastError;
  }

  public void scheduleRetry(LocalDateTime nextRetryAt, String operator) {
    transitTo(ExternalInteractionStatus.RETRY_WAIT, operator);
    this.nextRetryAt = nextRetryAt;
    this.retryCount++;
  }

  public void dispatchAgain(String operator) {
    transitTo(ExternalInteractionStatus.DISPATCHING, operator);
  }

  public void confirmSucceededAfterReconciliation(String resultRef, String operator) {
    transitTo(ExternalInteractionStatus.SUCCEEDED, operator);
    this.resultRef = resultRef;
  }

  public void confirmFailedAfterReconciliation(String lastError, String operator) {
    transitTo(ExternalInteractionStatus.FAILED, operator);
    this.lastError = lastError;
  }

  public void cancel(String operator) {
    transitTo(ExternalInteractionStatus.CANCELLED, operator);
  }

  /**
   * 状态迁移先行：附带字段在迁移成功后写入，保证非法迁移抛出异常时不留下部分字段变更。
   */
  private void transitTo(ExternalInteractionStatus target, String operator) {
    if (this.status == null || !this.status.canTransitTo(target)) {
      throw new DomainException(CoreDomainErrorCode.INVALID_STATUS)
        .withLogDetail("外部协作状态不允许从 %s 迁移到 %s".formatted(this.status, target));
    }
    this.status = target;
    if (target.isTerminal()) {
      this.completedAt = LocalDateTime.now();
    }
    this.markUpdated(operator);
  }

  /**
   * 外部事件关联：优先按 requestNo 命中，未命中再按外部任务 ID 回退；两者皆缺失时返回 false。
   */
  public boolean matches(String requestNo, String externalTaskId) {
    if (hasText(requestNo) && this.requestNo.equals(requestNo)) {
      return true;
    }
    return hasText(externalTaskId) && externalTaskId.equals(this.externalTaskId);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  @Override
  protected void validateInvariants() {
    if (this.status == null) {
      return; // 构造阶段字段尚未装配
    }
    if (this.applicationId == null || this.stepExecutionId == null || this.type == null
      || this.requestNo == null || this.requestNo.isBlank()) {
      throw new DomainException(CoreDomainErrorCode.INVALID_DATA)
        .withLogDetail("外部协作聚合不变量被破坏");
    }
  }

  public ApplicationId applicationId() {
    return applicationId;
  }

  public StepExecutionId stepExecutionId() {
    return stepExecutionId;
  }

  public InteractionType type() {
    return type;
  }

  public String requestNo() {
    return requestNo;
  }

  public String externalTaskId() {
    return externalTaskId;
  }

  public ExternalInteractionStatus status() {
    return status;
  }

  public String resultRef() {
    return resultRef;
  }

  public int retryCount() {
    return retryCount;
  }

  public LocalDateTime nextRetryAt() {
    return nextRetryAt;
  }

  public String lastError() {
    return lastError;
  }

  public LocalDateTime completedAt() {
    return completedAt;
  }
}
