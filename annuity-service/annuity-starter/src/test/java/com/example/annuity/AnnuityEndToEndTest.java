package com.example.annuity;

import com.example.approval.api.ApprovalFlowApi;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.approval.api.event.ApprovalInstanceApprovedEventDTO;
import com.example.core.application.listener.StepAutoAdvanceListener;
import com.example.core.application.service.BusinessOrchestrationAppService;
import com.example.core.domain.aggregate.root.BusinessApplication;
import com.example.core.domain.aggregate.valueobject.BusinessContext;
import com.example.core.domain.aggregate.valueobject.OperatorInfo;
import com.example.core.domain.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.aggregate.valueobject.business.AnnuityChannel;
import com.example.core.domain.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.core.domain.repository.ApplicationRepository;
import com.example.core.infrastructure.event.IntegrationEventSimulator;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 年金业务端到端集成测试
 * <p>
 * 演示完整业务链路：
 * <ol>
 *   <li>创建 BusinessApplication（FORM_DETAIL_INGESTION）→ 持久化</li>
 *   <li>调用 {@code BusinessOrchestrationAppService.advanceStep} 推进流程</li>
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
@Import(KernelDomainServiceTestConfiguration.class)
@DisplayName("年金业务端到端集成测试")
class AnnuityEndToEndTest {

  private static final String BIZ_TYPE_FORM_DETAIL = "FORM_DETAIL";
  private static final String STATUS_SUCCESS = "SUCCESS";

  @Autowired
  private BusinessOrchestrationAppService orchestrationService;

  @Autowired
  private ApplicationRepository applicationRepository;

  @Autowired
  private IntegrationEventSimulator eventSimulator;

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
    applicationRepository.save(app);
    return appId;
  }
}
