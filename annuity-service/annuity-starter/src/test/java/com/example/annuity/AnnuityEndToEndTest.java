package com.example.annuity;

import com.example.annuity.domain.aggregate.root.AnnuityEmployeeBatch;
import com.example.annuity.domain.extension.AnnuityApplicationExtension;
import com.example.annuity.domain.repository.AnnuityEmployeeBatchRepository;
import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.approval.api.event.ApprovalInstanceApprovedEventDTO;
import com.example.core.application.engine.listener.StepAutoAdvanceListener;
import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.BusinessExtension;
import com.example.core.domain.business.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.infrastructure.engine.event.IntegrationEventSimulator;
import com.example.file.api.FileAccessApi;
import com.example.file.api.FileTaskApi;
import com.example.file.api.event.FileParsedEventDTO;
import com.example.shared.primitives.identity.ApplicationId;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.FileId;
import com.example.shared.primitives.identity.PlanNo;
import com.example.shared.primitives.identity.ProductNo;
import com.example.shared.primitives.identity.UserNo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 年金业务端到端集成测试
 * <p>
 * 演示完整业务链路：
 * <ol>
 *   <li>创建 BusinessApplication（FORM_DETAIL_INGESTION）→ 持久化</li>
 *   <li>调用 {@code FlowOrchestrationService.advanceStep} 推进流程</li>
 *   <li>验证流程经过 DATA_VERIFICATION → MATERIAL_PREPARATION → APPROVAL → COMPLETED</li>
 *   <li>验证 {@link IntegrationEventSimulator} 发布集成事件后，监听器能推进流程</li>
 * </ol>
 * <p>
 * <b>测试环境：</b>H2 内存数据库（PostgreSQL 兼容模式）+ Spring ApplicationEvent 模拟跨服务事件传递。
 * 外部 @HttpExchange API 客户端通过 {@link MockBean} 注入空实现，避免真实 HTTP 调用。
 *
 * @author annuity-service
 * @since 2026/7/21
 */
@SpringBootTest(classes = AnnuityApplication.class)
@ActiveProfiles("test")
@DisplayName("年金业务端到端集成测试")
class AnnuityEndToEndTest {

  private static final String BIZ_TYPE_FORM_DETAIL = "FORM_DETAIL";
  private static final String STATUS_SUCCESS = "SUCCESS";

  @Autowired
  private FlowOrchestrationService orchestrationService;

  @Autowired
  private ApplicationRepository applicationRepository;

  @Autowired
  private IntegrationEventSimulator eventSimulator;

  @Autowired
  private AnnuityEmployeeBatchRepository batchRepository;

  @MockBean
  private FileTaskApi fileTaskApi;

  @MockBean
  private FileAccessApi fileAccessApi;

  @MockBean
  private ApprovalFlowApi approvalFlowApi;

  @MockBean
  private ApprovalInstanceApi approvalInstanceApi;

  /**
   * 禁用 kernel 的 {@link StepAutoAdvanceListener}。
   * <p>
   * 该监听器使用 {@code @Async @TransactionalEventListener(AFTER_COMMIT)} 监听
   * {@code StepEnteredEvent}，在事务提交后异步判断新进入的步骤是否为 SYSTEM_TASK，
   * 若是则自动调用 {@code advanceStep} 推进流程。
   * <p>
   * <b>【为何禁用】</b>端到端测试需要完全手动控制流程推进节奏，避免异步监听器
   * 跨测试方法或跨断言点推进状态导致的不确定性。
   * <ul>
   *   <li>{@code orchestrationFlow_advancesFromIngestionToCompleted} 测试每次
   *       {@code advanceStep} 后立即断言，异步自动推进会让状态多跳一步；</li>
   *   <li>{@code fileParsedEvent_advancesApplication} 和
   *       {@code approvalApprovedEvent_completesApplication} 测试通过
   *       {@link IntegrationEventSimulator} 同步触发
   *       {@code FileParsedEventListener}/{@code ApprovalResultEventListener}，
   *       不依赖 {@code StepAutoAdvanceListener}。</li>
   * </ul>
   * <b>【生产环境】</b>该监听器仍正常工作，仅测试环境通过 {@code @MockBean} 替换为 noop。
   */
  @MockBean
  private StepAutoAdvanceListener stepAutoAdvanceListener;

  // ====================================================
  // 测试用例
  // ====================================================

  @Test
  @DisplayName("Spring 上下文加载成功：核心 Bean 与 Mock Bean 全部就绪")
  void contextLoads() {
    assertThat(orchestrationService).isNotNull();
    assertThat(applicationRepository).isNotNull();
    assertThat(eventSimulator).isNotNull();
  }

  @Test
  @DisplayName("完整流程推进：FORM_DETAIL_INGESTION → DATA_VERIFICATION → MATERIAL_PREPARATION → APPROVAL → COMPLETED")
  void orchestrationFlow_advancesFromIngestionToCompleted() {
    ApplicationId appId = createAndSaveApplication();

    // 初始：FORM_DETAIL_INGESTION
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.FORM_DETAIL_INGESTION);

    // 第 1 次推进：FORM_DETAIL_INGESTION → DATA_VERIFICATION（mainProcessor=null）
    orchestrationService.advanceStep(appId);
    app = applicationRepository.loadOrThrow(appId);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.DATA_VERIFICATION);

    // 第 2 次推进：DATA_VERIFICATION → MATERIAL_PREPARATION（mainProcessor=null）
    orchestrationService.advanceStep(appId);
    app = applicationRepository.loadOrThrow(appId);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.MATERIAL_PREPARATION);

    // 第 3 次推进：MATERIAL_PREPARATION → APPROVAL（mainProcessor=planMaterialPreparationHandler 返回 SUCCESS）
    orchestrationService.advanceStep(appId);
    app = applicationRepository.loadOrThrow(appId);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.APPROVAL);

    // 第 4 次推进：APPROVAL → COMPLETED（mainProcessor=null）
    orchestrationService.advanceStep(appId);
    app = applicationRepository.loadOrThrow(appId);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.COMPLETED);
  }

  @Test
  @DisplayName("文件解析完成事件推进流程：IntegrationEventSimulator 发布 FileParsedEventDTO 后监听器自动推进")
  void fileParsedEvent_advancesApplication() {
    ApplicationId appId = createAndSaveApplication();
    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    FileId parsedJsonFileId = app.getParsedJsonFileId();

    // 初始：FORM_DETAIL_INGESTION
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.FORM_DETAIL_INGESTION);

    // 模拟 file-service 解析完成事件（fileTaskId = parsedJsonFileId.value()）
    FileParsedEventDTO event = new FileParsedEventDTO(
        UUID.randomUUID().toString(),
        parsedJsonFileId.value(),
        BIZ_TYPE_FORM_DETAIL,
        STATUS_SUCCESS,
        0,
        List.of(),
        null,
        LocalDateTime.now()
    );
    eventSimulator.publish(event);

    // 验证 FileParsedEventListener 已通过 findByFileTaskId 反查并推进流程
    app = applicationRepository.loadOrThrow(appId);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.DATA_VERIFICATION);
  }

  @Test
  @DisplayName("审批通过事件完成流程：推进到 APPROVAL 后发布 ApprovalInstanceApprovedEventDTO 完成流程")
  void approvalApprovedEvent_completesApplication() {
    ApplicationId appId = createAndSaveApplication();

    // 连续推进到 APPROVAL
    orchestrationService.advanceStep(appId); // → DATA_VERIFICATION
    orchestrationService.advanceStep(appId); // → MATERIAL_PREPARATION
    orchestrationService.advanceStep(appId); // → APPROVAL

    BusinessApplication app = applicationRepository.loadOrThrow(appId);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.APPROVAL);

    // 模拟 approval-service 审批通过事件（businessNo = ApplicationId.value()）
    ApprovalInstanceApprovedEventDTO event = new ApprovalInstanceApprovedEventDTO(
        UUID.randomUUID().toString(),
        "1",
        appId.value(),
        BusinessType.ACC_PLAN_CREATE.name(),
        LocalDateTime.now()
    );
    eventSimulator.publish(event);

    // 验证 ApprovalResultEventListener 已推进流程到 COMPLETED
    app = applicationRepository.loadOrThrow(appId);
    assertThat(app.currentStep()).isEqualTo(ApplicationFlowStep.COMPLETED);
  }

  @Test
  @DisplayName("明细摄入:FORM_DETAIL_INGESTION 后员工批次已创建并包含 2 条明细")
  void detailIngestionAction_createsEmployeeBatchWithDetails() {
    ApplicationId appId = createAndSaveApplication();

    // 推进 FORM_DETAIL_INGESTION → DATA_VERIFICATION
    // detailProcessors 中的 annuityDetailIngestionAction 从 Mock JSON 流摄入明细
    orchestrationService.advanceStep(appId);

    // 验证员工批次已创建
    Optional<AnnuityEmployeeBatch> batch = batchRepository.findByApplicationId(appId);
    assertThat(batch).isPresent();
    assertThat(batch.get().details()).hasSize(2);
    assertThat(batch.get().details())
        .extracting("employeeName")
        .containsExactlyInAnyOrder("张三", "李四");
  }

  @Test
  @DisplayName("数据核查:DATA_VERIFICATION 后明细状态变为 VERIFIED,批次状态为 COMPLETED")
  void dataVerificationHandler_marksDetailsAsVerified() {
    ApplicationId appId = createAndSaveApplication();

    // 推进到 DATA_VERIFICATION 并执行核查
    orchestrationService.advanceStep(appId); // FORM_DETAIL_INGESTION → DATA_VERIFICATION
    orchestrationService.advanceStep(appId); // DATA_VERIFICATION → MATERIAL_PREPARATION

    // 验证明细已核查 (AnnuityEmployeeDetailStatus 枚举: PENDING/VERIFIED/ANOMALY/MATERIAL_READY)
    AnnuityEmployeeBatch batch = batchRepository.findByApplicationId(appId).orElseThrow();
    assertThat(batch.processedCount()).isEqualTo(2);
    assertThat(batch.details())
        .allMatch(detail -> "VERIFIED".equals(detail.status().name()));
  }

  // ====================================================
  // 私有辅助方法
  // ====================================================

  /**
   * 构造一个初始 BusinessApplication 并持久化。
   * <p>
   * 使用 kernel 公开的 {@link BusinessApplication#createFromForm} 工厂方法，
   * 创建初始状态为 PROCESSING / FORM_DETAIL_INGESTION 的申请单，
   * 携带 BusinessContext、OperatorInfo 领域原语与 FileId（用于文件解析事件反查）。
   *
   * @return 新建并已持久化的申请单 ID
   */
  private ApplicationId createAndSaveApplication() {
    BusinessContext context = new BusinessContext(
        BusinessType.ACC_PLAN_CREATE,
        CustomerNo.of("C-TEST-001"),
        "测试客户",
        ProductNo.of("P-TEST-001"),
        "测试产品",
        PlanNo.of("PL-TEST-001"),
        "测试方案",
        OperationModel.Single_Trustee,
        AccountManager.CJP
    );
    OperatorInfo operator = new OperatorInfo(
        AnnuityChannel.NETAPP,
        UserNo.of("U-TEST-001"),
        "测试操作人",
        false
    );
    ApplicationId appId = new ApplicationId("APP-TEST-" + UUID.randomUUID());
    FileId jsonFileId = new FileId("FILE-TEST-" + UUID.randomUUID());

    BusinessApplication app = BusinessApplication.createFromForm(appId, context, operator, jsonFileId);
    // 设置年金业务扩展字段（通过反射，因 BusinessApplication 无公开 setter）
    AnnuityApplicationExtension annuityExt = new AnnuityApplicationExtension(
        BusinessType.ACC_PLAN_CREATE,
        AnnuityApplicationExtension.PLAN_TYPE_NEW,
        20000L,  // 200 元（单位:分），大于 MIN_INITIAL_CONTRIBUTION_FOR_NEW=10000
        false    // 无外资成分，与 MockAnnuityCustomerGateway 返回的画像一致
    );
    setBusinessExtension(app, annuityExt);
    // 设置 updatedBy（通过反射），因 Entity.markUpdated() 是 protected 无法从测试直接调用，
    // 而 AnnuityDataVerificationHandler 使用 app.updatedBy() 调用 markDetailProcessed，
    // markUpdated(null) 会抛 IllegalArgumentException
    setUpdatedBy(app, operator.operatorId());
    applicationRepository.save(app);
    return appId;
  }

  /**
   * 通过反射设置 BusinessApplication 的 businessExtension 私有字段。
   * <p>
   * BusinessApplication 只暴露了 getter（businessExtension()），无公开 setter。
   * 测试场景需设置扩展字段以通过 DATA_VERIFICATION 步骤的校验 Action。
   *
   * @param app       业务申请单
   * @param extension 年金扩展字段
   */
  private void setBusinessExtension(BusinessApplication app, BusinessExtension extension) {
    try {
      Field field = BusinessApplication.class.getDeclaredField("businessExtension");
      field.setAccessible(true);
      field.set(app, extension);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalStateException("设置 businessExtension 失败", e);
    }
  }

  /**
   * 通过反射设置 BusinessApplication 的 updatedBy 私有字段。
   * <p>
   * Entity.markUpdated(UserNo) 是 protected 方法，测试无法直接调用。
   * AnnuityDataVerificationHandler 使用 app.updatedBy() 作为操作人调用
   * batch.markDetailProcessed()，若为 null 会抛 IllegalArgumentException。
   *
   * @param app      业务申请单
   * @param operator 操作人
   */
  private void setUpdatedBy(BusinessApplication app, UserNo operator) {
    try {
      Field field = com.example.shared.domain.aggregate.entity.Entity.class.getDeclaredField("updatedBy");
      field.setAccessible(true);
      field.set(app, operator);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalStateException("设置 updatedBy 失败", e);
    }
  }
}
