package com.example.core.application.engine.acceptance;

import com.example.core.api.application.command.AdvanceStepCommand;
import com.example.core.application.engine.pipeline.StepPipelineExecutor;
import com.example.core.application.engine.service.FlowOrchestrationService;
import com.example.core.application.engine.service.StepDefinitionResolver;
import com.example.core.application.engine.service.StepRouteConfigAssembler;
import com.example.core.domain.business.aggregate.root.BusinessApplication;
import com.example.core.domain.business.aggregate.valueobject.BusinessContext;
import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.repository.ApplicationRepository;
import com.example.core.domain.engine.aggregate.root.StepExecution;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.domain.engine.aggregate.valueobject.PipelineExecutionResult;
import com.example.core.domain.engine.aggregate.valueobject.StepCode;
import com.example.core.domain.engine.aggregate.valueobject.StepDefinition;
import com.example.core.domain.engine.aggregate.valueobject.config.StepRouteConfig;
import com.example.core.domain.engine.aggregate.valueobject.enums.definition.TaskType;
import com.example.core.domain.engine.aggregate.valueobject.enums.status.StepExecutionStatus;
import com.example.core.domain.engine.aggregate.valueobject.enums.workflow.ApplicationFlowStep;
import com.example.core.domain.engine.gateway.BusinessConfigGateway;
import com.example.core.domain.engine.repository.ExternalInteractionRepository;
import com.example.core.domain.engine.repository.StepExecutionRepository;
import com.example.core.domain.engine.service.registry.BusinessFactExtractorRegistry;
import com.example.core.domain.engine.service.registry.StepActionHandlerRegistry;
import com.example.core.domain.engine.spi.StepActionHandler;
import com.example.core.types.ApplicationId;
import com.example.core.types.Channel;
import com.example.core.types.CustomerNo;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.core.types.StepExecutionId;
import com.example.shared.identifier.contract.IdService;
import com.example.shared.lock.DistributedLock;
import com.example.shared.lock.LockHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §21.2 第 2 条 / §21.5 第 3 条并发推进验收（批次 43-1）。
 * <p>
 * kernel 仓储层没有 H2/Spring 集成设施，从零搭建真数据库上下文会引入线程调度不确定性；
 * 本测试改用<b>确定性并发</b>：内存 {@link DistributedLock} 按申请单 key 互斥（等价于多节点
 * 共享一把分布式锁），状态化 {@link StepExecutionRepository} 让 {@code findByRequestNo} 真实反映
 * {@code save} 结果（等价于唯一索引 + 幂等查询），{@link CountDownLatch} 只负责让两个线程同时
 * 起跑、结果不依赖调度顺序。
 * <p>
 * 场景：两个节点并发用相同 requestNo 调 {@code advanceApplication}。锁保证串行，第一个节点
 * 保存步骤执行后，第二个节点在锁内查到已存在的 requestNo 被幂等短路，因而只产生一次有效跃迁。
 */
@DisplayName("并发推进只产生一次有效跃迁（批次 43-1）")
class ConcurrentAdvanceAcceptanceTest {

  private static final ApplicationId APP_ID = new ApplicationId("APP-001");
  private static final String REQUEST_NO = "REQ-CONCURRENT";

  /** 状态化仓储的「数据库」：key 为 requestNo。 */
  private final Map<String, StepExecution> saved = new ConcurrentHashMap<>();

  private StepExecutionRepository stepExecutionRepository;
  private ApplicationRepository applicationRepository;
  private FlowOrchestrationService service;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    StepActionHandlerRegistry handlerRegistry = mock(StepActionHandlerRegistry.class);
    BusinessFactExtractorRegistry factExtractorRegistry = mock(BusinessFactExtractorRegistry.class);
    BusinessConfigGateway configGateway = mock(BusinessConfigGateway.class);
    applicationRepository = mock(ApplicationRepository.class);
    StepPipelineExecutor pipelineExecutor = mock(StepPipelineExecutor.class);
    StepDefinitionResolver stepDefinitionResolver = mock(StepDefinitionResolver.class);
    StepRouteConfigAssembler stepRouteConfigAssembler = mock(StepRouteConfigAssembler.class);
    stepExecutionRepository = mock(StepExecutionRepository.class);
    ExternalInteractionRepository externalInteractionRepository = mock(ExternalInteractionRepository.class);
    ObjectProvider<DistributedLock> lockProvider = mock(ObjectProvider.class);
    IdService idService = mock(IdService.class);

    // 状态化仓储：save 写入后 findByRequestNo 立刻可见，幂等短路才有真实语义
    when(stepExecutionRepository.findByRequestNo(anyString()))
      .thenAnswer(inv -> Optional.ofNullable(saved.get(inv.<String>getArgument(0))));
    doAnswer(inv -> {
      StepExecution execution = inv.getArgument(0);
      saved.put(execution.requestNo(), execution);
      return null;
    }).when(stepExecutionRepository).save(any(StepExecution.class));
    when(stepExecutionRepository.findByApplicationAndStep(any(), any())).thenReturn(List.of());

    AtomicLong idSequence = new AtomicLong();
    when(idService.nextLongId(eq(StepExecutionId.class), anyString()))
      .thenAnswer(inv -> StepExecutionId.of(idSequence.incrementAndGet()));

    // 内存锁按 key 互斥，等价于多节点共享同一把分布式锁
    when(lockProvider.getIfAvailable()).thenReturn(new InProcessDistributedLock());

    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    TransactionTemplate txTemplate = new TransactionTemplate(txManager);

    service = new FlowOrchestrationService(
      handlerRegistry, factExtractorRegistry, configGateway,
      applicationRepository, pipelineExecutor, txTemplate,
      stepDefinitionResolver, stepRouteConfigAssembler,
      stepExecutionRepository, idService, externalInteractionRepository, lockProvider);

    stubStep(handlerRegistry, configGateway, pipelineExecutor,
      stepDefinitionResolver, stepRouteConfigAssembler);
  }

  @Test
  @DisplayName("两个节点并发推进相同 requestNo：只保存一条步骤执行、只跃迁一次")
  void concurrentAdvance_sameRequestNo_executesOnce() throws Exception {
    AdvanceStepCommand command = new AdvanceStepCommand(
      APP_ID.value(), "FORM_DETAIL_INGESTION", REQUEST_NO, "U-1", Map.of());

    int nodes = 2;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(nodes);
    ExecutorService pool = Executors.newFixedThreadPool(nodes);
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < nodes; i++) {
      futures.add(pool.submit(() -> {
        try {
          start.await();
          service.advanceApplication(command);
        } finally {
          done.countDown();
        }
        return null;
      }));
    }

    start.countDown();
    assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
    for (Future<?> future : futures) {
      future.get(1, TimeUnit.SECONDS);
    }
    pool.shutdownNow();

    // 锁串行化 + 幂等短路：只有第一个节点真正执行了推进
    assertThat(saved).hasSize(1);
    assertThat(saved.get(REQUEST_NO).requestNo()).isEqualTo(REQUEST_NO);
    verify(stepExecutionRepository, times(1)).save(any(StepExecution.class));
    verify(applicationRepository, times(1)).save(any(BusinessApplication.class));
  }

  private void stubStep(StepActionHandlerRegistry handlerRegistry,
                        BusinessConfigGateway configGateway,
                        StepPipelineExecutor pipelineExecutor,
                        StepDefinitionResolver stepDefinitionResolver,
                        StepRouteConfigAssembler stepRouteConfigAssembler) {
    BusinessApplication app = mock(BusinessApplication.class);
    when(applicationRepository.loadOrThrow(APP_ID)).thenReturn(app);
    when(app.currentStep()).thenReturn(ApplicationFlowStep.FORM_DETAIL_INGESTION);
    when(app.buildConfigQueryContext()).thenReturn(BusinessMetaContext.of(context()));
    when(configGateway.getExtractorConfig(any())).thenReturn(null);

    StepDefinition step = StepDefinition.business(
      StepCode.of("FORM_DETAIL_INGESTION"), TaskType.SYSTEM_TASK, "mainHandler", List.of());
    when(stepDefinitionResolver.resolve(app)).thenReturn(step);
    StepRouteConfig route = new StepRouteConfig(
      ApplicationFlowStep.FORM_DETAIL_INGESTION, ApplicationFlowStep.DATA_VERIFICATION,
      StepRouteConfig.StepTaskType.SYSTEM_TASK, List.of(), "mainHandler", List.of(), List.of());
    when(stepRouteConfigAssembler.toRouteConfig(any(StepDefinition.class))).thenReturn(route);

    StepActionHandler handler = mock(StepActionHandler.class);
    when(handlerRegistry.get("mainHandler")).thenReturn(handler);
    when(handler.execute(any(), any())).thenReturn(StepExecutionStatus.SUCCESS);
    when(pipelineExecutor.executeChain(any(), any(), any()))
      .thenReturn(mock(PipelineExecutionResult.class));
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

  /** 按 key 互斥的进程内锁桩：模拟多节点共享同一把分布式锁，不引入 Redis 依赖。 */
  private static class InProcessDistributedLock implements DistributedLock {

    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Override
    public Optional<LockHandle> tryLock(String key, long waitTime, long leaseTime, TimeUnit unit) {
      throw new UnsupportedOperationException("并发验收只经 lockAndRun 模板方法");
    }

    @Override
    public <T> T lockAndRun(String key, long waitTime, long leaseTime, TimeUnit unit,
                            Supplier<T> task) {
      ReentrantLock lock = locks.computeIfAbsent(key, ignored -> new ReentrantLock());
      lock.lock();
      try {
        return task.get();
      } finally {
        lock.unlock();
      }
    }
  }
}
