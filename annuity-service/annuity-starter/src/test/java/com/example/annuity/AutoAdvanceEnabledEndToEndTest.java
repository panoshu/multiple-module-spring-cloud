package com.example.annuity;

import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.core.adapter.configuration.KernelProductionReadinessValidator;
import com.example.core.application.engine.listener.StepAutoAdvanceListener;
import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.ApplicationSource;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.gateway.ParsedFileReferenceGateway;
import com.example.core.domain.business.repository.ApplicationRepository;
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
import com.example.file.api.FileAccessApi;
import com.example.file.api.FileTaskApi;
import com.example.file.types.FileId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;

/**
 * §21.5 第 8 条端到端验收（批次 44）：<b>不禁用自动推进机制</b>。
 * <p>
 * 现有 {@link AnnuityEndToEndTest} 用 {@code @MockitoBean} 把 {@link StepAutoAdvanceListener}
 * 替换为 noop 以手动控制推进节奏，其确定性断言仍有价值，故保留不动；本类<b>刻意不声明</b>
 * 该 {@code @MockitoBean}，让真实监听器在 Spring 上下文中装配，验证生产形态的自动推进链路。
 * <p>
 * 使用独立 H2 库（{@code auto_advance_e2e}）与现有 E2E 隔离，避免共享内存库的数据互相污染；
 * 由于 context 配置不同（少一个 mock bean、数据源不同），Spring 会创建独立 ApplicationContext。
 * <p>
 * 流程：通过正常领域入口 {@code createFromForm} 建单（不直接预置 DB 记录）→ 一次手动
 * {@code advanceStep} 触发首个 {@code StepEnteredEvent} → {@code @Async @TransactionalEventListener}
 * 在 AFTER_COMMIT 后按版本化定义的 TaskType 级联推进 SYSTEM_TASK 步骤 → 遇到 USER_TASK
 * （{@code APPROVAL}）挂起。轮询等待该确定终点，避免依赖异步线程的具体调度时序。
 */
@SpringBootTest(
  classes = AnnuityApplication.class,
  properties = "spring.datasource.url=jdbc:h2:mem:auto_advance_e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
@DisplayName("自动推进未被禁用的端到端验收（批次 44）")
class AutoAdvanceEnabledEndToEndTest {

  private static final long AWAIT_TIMEOUT_SECONDS = 20L;

  @Autowired
  private FlowOrchestrationService orchestrationService;

  @Autowired
  private ApplicationRepository applicationRepository;

  @Autowired
  private JdbcClient jdbcClient;

  @Autowired
  private StepAutoAdvanceListener autoAdvanceListener;

  @Autowired
  private com.example.annuity.domain.repository.AnnuityApplicationExtensionStore extensionStore;

  @Autowired
  private ParsedFileReferenceGateway parsedFileGateway;

  @Autowired
  private KernelProductionReadinessValidator readinessValidator;

  @MockitoBean
  private FileTaskApi fileTaskApi;

  @MockitoBean
  private FileAccessApi fileAccessApi;

  @MockitoBean
  private ApprovalFlowApi approvalFlowApi;

  @MockitoBean
  private ApprovalInstanceApi approvalInstanceApi;

  // 刻意不声明 StepAutoAdvanceListener 的 @MockitoBean —— 自动推进机制不禁用

  @Test
  @DisplayName("真实监听器装配，一次手动推进后 SYSTEM_TASK 级联至 USER_TASK 挂起")
  void autoAdvance_notDisabled_drivesSystemTasksToUserTask() throws Exception {
    // 关键前置：确认这次上下文里不是 noop mock，而是生产形态的监听器
    assertThat(mockingDetails(autoAdvanceListener).isMock()).isFalse();

    // 正常领域入口建单，不预置流程实例、不直接插 DB
    ApplicationId appId = createAndSaveApplication();
    assertThat(applicationRepository.loadOrThrow(appId).currentStep())
      .isEqualTo(ApplicationFlowStep.FORM_DETAIL_INGESTION);

    // 只手动点火一次；其余 SYSTEM_TASK 步骤应由 AFTER_COMMIT + @Async 自动级联完成
    orchestrationService.advanceStep(appId);

    awaitStep(appId, ApplicationFlowStep.APPROVAL);

    // 至少 3 次步骤尝试（手动 1 次 + 自动至少 2 次），证明自动推进真实发生
    Long executions = jdbcClient
      .sql("SELECT COUNT(*) FROM t_business_step_execution WHERE application_id = ?")
      .param(appId.value())
      .query(Long.class).single();
    assertThat(executions).isGreaterThanOrEqualTo(3L);
  }

  /** 轮询等待确定终点（APPROVAL 为 USER_TASK，自动推进到此挂起），不依赖异步调度顺序。 */
  private void awaitStep(ApplicationId appId, ApplicationFlowStep expected) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(AWAIT_TIMEOUT_SECONDS);
    ApplicationFlowStep last = null;
    while (System.nanoTime() < deadline) {
      last = applicationRepository.loadOrThrow(appId).currentStep();
      if (last == expected) {
        return;
      }
      Thread.sleep(50L);
    }
    throw new AssertionError("自动推进未能在 " + AWAIT_TIMEOUT_SECONDS + "s 内到达 "
      + expected + "，实际停留在 " + last);
  }

  private ApplicationId createAndSaveApplication() {
    BusinessContext context = new BusinessContext(
      BusinessType.ACC_PLAN_CREATE,
      CustomerNo.of("C-TEST-001"), "测试客户",
      ProductNo.of("P-TEST-001"), "测试产品",
      PlanNo.of("PL-TEST-001"), "测试方案",
      OperatingMode.TRUSTEE_ONLY, AccountManager.CJP
    );
    OperatorInfo operator = new OperatorInfo(
      Channel.NETAPP, "U-TEST-001", "测试操作人", false);
    ApplicationId appId = new ApplicationId("APP-AUTO-" + UUID.randomUUID());
    FileId jsonFileId = new FileId("FILE-AUTO-" + UUID.randomUUID());

    BusinessApplication app = BusinessApplication.createFromForm(
      appId, new ApplicationSource(new FormId("FORM-AUTO-001"), BatchId.of("BATCH-AUTO-001")),
      context, operator, DefinitionVersion.initial()
    );
    extensionStore.save(appId, new AnnuityApplicationExtension(
      BusinessType.ACC_PLAN_CREATE, AnnuityApplicationExtension.PLAN_TYPE_NEW,
      20000L, false));
    parsedFileGateway.save(appId, jsonFileId);
    setUpdatedBy(app, operator.operatorId());
    applicationRepository.save(app);
    return appId;
  }

  @Test
  @DisplayName("缺省配置下 fail-fast 校验器仍按生产形态装配（批次 48 未静默降级）")
  void failFastValidator_isAssembledByDefaultInE2eContext() {
    assertThat(readinessValidator).isNotNull();
    assertThat(mockingDetails(readinessValidator).isMock()).isFalse();
  }

  /** 与现有 E2E 一致：Entity.markUpdated 为 protected，明细核查 Handler 需要 updatedBy 非空。 */
  private void setUpdatedBy(BusinessApplication app, String operator) {
    try {
      Field field = com.example.shared.domain.aggregate.entity.Entity.class.getDeclaredField("updatedBy");
      field.setAccessible(true);
      field.set(app, operator);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalStateException("设置 updatedBy 失败", e);
    }
  }
}
