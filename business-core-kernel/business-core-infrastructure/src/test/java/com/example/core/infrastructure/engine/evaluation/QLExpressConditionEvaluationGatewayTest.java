package com.example.core.infrastructure.engine.evaluation;

import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.types.OperatingMode;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.types.CustomerNo;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.shared.exception.SystemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
      OperatingMode.TRUSTEE_ONLY,
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
      CustomerNo.of("C001"), ProductNo.of("P001"), OperatingMode.TRUSTEE_ONLY,
      PlanNo.of("PL001"), BusinessType.ACC_PLAN_CREATE, AccountManager.BOC, null);

    assertThat(gateway.evaluate(null, pureFacts)).isTrue();
    assertThat(gateway.evaluate("   ", pureFacts)).isTrue();
    assertThat(gateway.evaluate("", emptyContext)).isTrue();
  }

  @Test
  @DisplayName("表达式语法错误显式失败,不静默返回 false")
  void testEvaluate_SyntaxError_ThrowsSystemException() {
    String invalidExpression = "facts.amount > &&"; // 故意写错的语法
    Map<String, Object> pureFacts = Map.of("amount", 100);

    // 表达式错误属于配置缺陷,必须显式失败,不能当作条件不满足跳过动作
    assertThatThrownBy(() -> gateway.evaluate(invalidExpression, pureFacts))
      .isInstanceOf(SystemException.class);
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
  @DisplayName("领域对象方法访问被安全策略拦截时显式失败")
  void testEvaluate_DomainObjectAccess_FailsExplicitly() {
    // 默认隔离策略会拦截 accountManager.getValue() 等反射调用,
    // 该求值异常属于配置问题,须显式抛出而非静默返回 false
    String expressionWithMethodCall = "accountManager.getValue() == '1100'";
    BusinessMetaContext context = new BusinessMetaContext(
      CustomerNo.of("C001"), ProductNo.of("P001"), OperatingMode.TRUSTEE_ONLY,
      PlanNo.of("PL001"), BusinessType.ACC_PLAN_CREATE, AccountManager.BOC, new ConcurrentHashMap<>());

    assertThatThrownBy(() -> gateway.evaluate(expressionWithMethodCall, context))
      .isInstanceOf(SystemException.class);
  }
}
