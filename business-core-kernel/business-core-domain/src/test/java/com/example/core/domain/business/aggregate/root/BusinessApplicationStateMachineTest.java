package com.example.core.domain.business.aggregate.root;

import com.example.core.domain.business.aggregate.valueobject.ApplicationSource;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.enums.status.ApplicationStatus;
import com.example.core.domain.engine.aggregate.valueobject.DefinitionVersion;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.core.types.ApplicationId;
import com.example.core.types.BatchId;
import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.FormId;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.file.types.FileId;
import com.example.shared.domain.aggregate.valueobject.Version;
import com.example.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BusinessApplication 状态机")
class BusinessApplicationStateMachineTest {

  @Test
  @DisplayName("创建后进入 RUNNING")
  void createFromForm_startsRunning() {
    assertThat(app().status()).isEqualTo(ApplicationStatus.RUNNING);
  }

  @Test
  @DisplayName("完成当前步骤推进步骤、发布事件并递增版本")
  void completeCurrentStep_advancesAndMarksUpdated() {
    BusinessApplication app = app();
    long version = app.version().value();

    app.completeCurrentStep(ApplicationFlowStep.DATA_VERIFICATION);

    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.DATA_VERIFICATION);
    assertThat(app.status()).isEqualTo(ApplicationStatus.RUNNING);
    assertThat(app.version().value()).isEqualTo(version + 1);
    assertThat(app.updatedBy()).isEqualTo("U-TEST");
    assertThat(app.domainEvents()).hasSize(2);
  }

  @Test
  @DisplayName("推进到 COMPLETED 时进入完成终态")
  void completeCurrentStep_toCompleted_setsCompleted() {
    BusinessApplication app = app();

    app.completeCurrentStep(ApplicationFlowStep.COMPLETED);

    assertThat(app.status()).isEqualTo(ApplicationStatus.COMPLETED);
    assertThat(app.completeTime()).isNotNull();
  }

  @Test
  @DisplayName("终态不可继续推进步骤")
  void terminalStateCannotAdvance() {
    BusinessApplication completed = app();
    completed.complete();
    BusinessApplication rejected = app();
    rejected.reject();
    BusinessApplication withdrawn = app();
    withdrawn.withdraw();
    BusinessApplication cancelled = app();
    cancelled.cancel();

    for (BusinessApplication terminal : List.of(completed, rejected, withdrawn, cancelled)) {
      assertThatThrownBy(() -> terminal.completeCurrentStep(ApplicationFlowStep.DATA_VERIFICATION))
        .isInstanceOf(DomainException.class);
    }
  }

  @Test
  @DisplayName("后继步骤为空时显式失败")
  void completeCurrentStep_rejectsNull() {
    BusinessApplication app = app();

    assertThatThrownBy(() -> app.completeCurrentStep(null))
      .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("重复推进同一节点保持幂等")
  void completeCurrentStep_sameStepIsIdempotent() {
    BusinessApplication app = app();
    long version = app.version().value();
    int events = app.domainEvents().size();

    app.completeCurrentStep(ApplicationFlowStep.FORM_DETAIL_INGESTION);

    assertThat(app.version().value()).isEqualTo(version);
    assertThat(app.domainEvents()).hasSize(events);
  }

  @Test
  @DisplayName("挂起、恢复、失败、重试、等待外部按迁移表生效")
  void stateTransitionsFollowTable() {
    BusinessApplication app = app();

    app.waitForUser();
    assertThat(app.status()).isEqualTo(ApplicationStatus.WAITING_USER);
    app.start();
    assertThat(app.status()).isEqualTo(ApplicationStatus.RUNNING);
    app.fail();
    assertThat(app.status()).isEqualTo(ApplicationStatus.FAILED);
    app.retry();
    assertThat(app.status()).isEqualTo(ApplicationStatus.RUNNING);
    app.waitForExternal();
    assertThat(app.status()).isEqualTo(ApplicationStatus.WAITING_EXTERNAL);
    app.start();
    assertThat(app.status()).isEqualTo(ApplicationStatus.RUNNING);
  }

  @Test
  @DisplayName("非法状态迁移显式失败")
  void illegalTransitionThrows() {
    BusinessApplication failed = app();
    failed.fail();

    assertThatThrownBy(failed::complete).isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("驳回与撤回为终态且可幂等重入")
  void rejectAndWithdrawAreTerminal() {
    BusinessApplication rejected = app();
    rejected.reject();
    assertThat(rejected.status()).isEqualTo(ApplicationStatus.REJECTED);
    assertThatThrownBy(() -> rejected.completeCurrentStep(ApplicationFlowStep.DATA_VERIFICATION))
      .isInstanceOf(DomainException.class);
    rejected.reject();
    assertThat(rejected.status()).isEqualTo(ApplicationStatus.REJECTED);

    BusinessApplication withdrawn = app();
    withdrawn.withdraw();
    assertThat(withdrawn.status()).isEqualTo(ApplicationStatus.WITHDRAWN);
    withdrawn.withdraw();
    assertThat(withdrawn.status()).isEqualTo(ApplicationStatus.WITHDRAWN);
  }

  @Test
  @DisplayName("存量 PROCESSING 状态对外归一为 RUNNING")
  void legacyProcessingStatusIsNormalized() {
    LocalDateTime now = LocalDateTime.now();
    BusinessApplication app = BusinessApplication.reconstitute(
      new ApplicationId("APP-LEGACY"), "U-C", "U-C", now.minusDays(1), now, Version.initial(),
      BatchId.of("B-1"), new FormId("F-1"), context(), operator(),
      new FileId("FILE-1"), 0, DefinitionVersion.initial(),
      ApplicationStatus.PROCESSING, ApplicationFlowStep.FORM_DETAIL_INGESTION,
      now, null, List.of(), null
    );

    assertThat(app.status()).isEqualTo(ApplicationStatus.RUNNING);
    assertThat(app.status().canTransitTo(ApplicationStatus.COMPLETED)).isTrue();
  }

  private BusinessApplication app() {
    return BusinessApplication.createFromForm(
      new ApplicationId("APP-1"),
      new ApplicationSource(new FormId("F-1"), BatchId.of("B-1")),
      context(), operator(), new FileId("FILE-1"), DefinitionVersion.initial()
    );
  }

  private BusinessContext context() {
    return new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      CustomerNo.of("C-001"), "客户",
      ProductNo.of("P-001"), "产品",
      PlanNo.of("PL-001"), "方案",
      OperatingMode.TRUSTEE_ONLY, AccountManager.CJP
    );
  }

  private OperatorInfo operator() {
    return new OperatorInfo(Channel.NETAPP, "U-TEST", "操作人", false);
  }
}
