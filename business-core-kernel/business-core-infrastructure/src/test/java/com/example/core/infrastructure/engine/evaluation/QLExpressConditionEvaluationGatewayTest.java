package com.example.core.infrastructure.engine.evaluation;

import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.business.aggregate.valueobject.business.OperationModel;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.shared.primitives.identity.CustomerNo;
import com.example.shared.primitives.identity.PlanNo;
import com.example.shared.primitives.identity.ProductNo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QLExpressConditionEvaluationGateway 单元测试")
class QLExpressConditionEvaluationGatewayTest {

  private QLExpressConditionEvaluationGateway gateway;

  @BeforeEach
  void setUp() {
    gateway = new QLExpressConditionEvaluationGateway();
  }

  @Test
  @DisplayName("正常求值 - 基于 BusinessMetaContext 且主要依赖 extensionFacts (推荐模式)")
  void testEvaluate_WithContext_FactsAccess_Success() {
    // 【推荐做法】：将需要参与规则判断的领域对象属性，扁平化放入 facts 中，避免 QLExpress 安全策略拦截
    String expression = "facts.businessTypeCode == 'ACC_PLAN_CREATE' && facts.creditScore > 600";

    Map<String, Object> facts = new ConcurrentHashMap<>();
    facts.put("businessTypeCode", BusinessType.ACC_PLAN_CREATE.name()); // 扁平化枚举
    facts.put("creditScore", 750);

    BusinessMetaContext context = new BusinessMetaContext(
      CustomerNo.of("C001"),
      ProductNo.of("P001"),
      OperationModel.Single_Trustee,
      PlanNo.of("PL001"),
      BusinessType.ACC_PLAN_CREATE,
      AccountManager.BOC,
      facts
    );

    boolean result = gateway.evaluate(expression, context);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("正常求值 - 基于 pureFacts (明细层降维)")
  void testEvaluate_WithPureFacts_Success() {
    String expression = "facts.amount >= 1000 && facts.isVip == true";

    Map<String, Object> pureFacts = Map.of(
      "amount", 1500,
      "isVip", true
    );

    boolean result = gateway.evaluate(expression, pureFacts);

    assertThat(result).isTrue();
  }

  @Test
  @DisplayName("边界条件 - 空或空白表达式应默认返回 true (放行)")
  void testEvaluate_NullOrBlankExpression_ReturnsTrue() {
    Map<String, Object> pureFacts = Map.of("amount", 100);
    BusinessMetaContext emptyContext = new BusinessMetaContext(
      CustomerNo.of("C001"), ProductNo.of("P001"), OperationModel.Single_Trustee,
      PlanNo.of("PL001"), BusinessType.ACC_PLAN_CREATE, AccountManager.BOC, null);

    assertThat(gateway.evaluate(null, pureFacts)).isTrue();
    assertThat(gateway.evaluate("   ", pureFacts)).isTrue();
    assertThat(gateway.evaluate("", emptyContext)).isTrue();
  }

  @Test
  @DisplayName("容错策略 - 表达式语法错误应捕获异常并返回 false (不通过)")
  void testEvaluate_SyntaxError_ReturnsFalseForSafety() {
    String invalidExpression = "facts.amount > &&"; // 故意写错的语法
    Map<String, Object> pureFacts = Map.of("amount", 100);

    // 按照网关的容错设计，校验异常应视为不通过，返回 false，且不能抛出未捕获异常导致管道崩溃
    boolean result = gateway.evaluate(invalidExpression, pureFacts);

    assertThat(result).isFalse();
  }

  @Test
  @DisplayName("并发安全 - 多线程并发执行同一表达式不应报错且结果正确")
  void testEvaluate_ConcurrentExecution_ThreadSafe() throws InterruptedException {
    String expression = "facts.counter > 5";
    Map<String, Object> pureFacts = new ConcurrentHashMap<>();
    pureFacts.put("counter", 10);

    int threadCount = 50;
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger errorCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      CompletableFuture.runAsync(() -> {
        try {
          boolean result = gateway.evaluate(expression, pureFacts);
          if (result) {
            successCount.incrementAndGet();
          }
        } catch (Exception e) {
          errorCount.incrementAndGet();
        } finally {
          latch.countDown();
        }
      });
    }

    latch.await();

    assertThat(successCount.get()).isEqualTo(threadCount);
    assertThat(errorCount.get()).isEqualTo(0);
  }

  @Test
  @DisplayName("领域对象访问说明 - 若需直接调用 Enum/Record 方法，需配置白名单")
  void testEvaluate_DomainObjectAccess_RequiresWhitelist_Or_Flattening() {
    // 【场景说明】：如果您的业务规则必须写成 "accountManager.value == '1100'"
    // 在 InitOptions.DEFAULT_OPTIONS (隔离模式) 下，QLExpress 会拦截 .getValue() 或 .value 的调用并抛出异常。

    // ✅ 解决方案 A (推荐)：在构建 context 时，将需要的值扁平化放入 facts (如上一个测试所示)

    // ✅ 解决方案 B：修改 QLExpressConditionEvaluationGateway 的构造函数，添加白名单：

        // InitOptions options = InitOptions.builder()
        //     .securityStrategy(QLSecurityStrategy.whiteList(AccountManager.class, CustomerNo.class))
        //     .build();
        // this.runner = new Express4Runner(options);

    // 此处我们验证：在默认配置下，直接调用方法会触发我们代码中的 catch 块，返回 false (Fail-Safe)
    String expressionWithMethodCall = "accountManager.getValue() == '1100'";
    Map<String, Object> pureFacts = new ConcurrentHashMap<>();
    // 注意：纯 facts 模式下，gateway 会将 pureFacts 放入 key "facts" 中。
    // 如果要测试 accountManager，需要使用带 BusinessMetaContext 的重载方法。

    BusinessMetaContext context = new BusinessMetaContext(
      CustomerNo.of("C001"), ProductNo.of("P001"), OperationModel.Single_Trustee,
      PlanNo.of("PL001"), BusinessType.ACC_PLAN_CREATE, AccountManager.BOC, pureFacts);

    // 因为默认隔离策略拦截了 getValue()，会抛出异常，被 catch 捕获后返回 false
    boolean result = gateway.evaluate(expressionWithMethodCall, context);

    assertThat(result).isFalse(); // 验证了 Fail-Safe 机制生效
  }
}
