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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AviatorConditionEvaluationGateway} 单元测试
 * <p>
 * 验证基于 Aviator 表达式引擎的条件求值网关,覆盖:
 * <ul>
 *   <li>空表达式短路返回 true</li>
 *   <li>基于 BusinessMetaContext 的变量求值(含 facts 字典访问)</li>
 *   <li>基于纯 facts Map 的降维求值(明细层场景)</li>
 *   <li>非法表达式容错(返回 false 而非抛异常)</li>
 *   <li>表达式缓存(相同表达式重复求值不报错)</li>
 * </ul>
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/7/23
 */
@DisplayName("AviatorConditionEvaluationGateway 条件求值测试")
class AviatorConditionEvaluationGatewayTest {

  private AviatorConditionEvaluationGateway gateway;

  @BeforeEach
  void setUp() {
    gateway = new AviatorConditionEvaluationGateway();
  }

  @Test
  @DisplayName("空表达式短路返回 true:无前置条件过滤")
  void evaluate_blankExpression_returnsTrue() {
    BusinessMetaContext context = buildContext(Map.of());
    assertThat(gateway.evaluate(null, context)).isTrue();
    assertThat(gateway.evaluate("", context)).isTrue();
    assertThat(gateway.evaluate("   ", context)).isTrue();
  }

  @Test
  @DisplayName("BusinessMetaContext 变量注入:上下文变量非空且 facts 字符串比较生效")
  void evaluate_contextVariable_injectedAndAccessible() {
    BusinessMetaContext context = buildContext(Map.of("customerNo", "C-001"));
    // Aviator 使用 nil 判空,验证上下文变量已注入
    assertThat(gateway.evaluate("businessType != nil", context)).isTrue();
    assertThat(gateway.evaluate("customerNo != nil", context)).isTrue();
    // 通过 facts 字典做字符串比较(record 组件的属性访问因 Aviator 反射限制不通用,统一走 facts)
    assertThat(gateway.evaluate("facts['customerNo'] == 'C-001'", context)).isTrue();
    assertThat(gateway.evaluate("facts['customerNo'] == 'C-999'", context)).isFalse();
  }

  @Test
  @DisplayName("facts 字典访问:facts['hasForeignInvestment'] 布尔判断")
  void evaluate_contextFactsMap_booleanAccess() {
    BusinessMetaContext context = buildContext(Map.of("hasForeignInvestment", true));
    assertThat(gateway.evaluate("facts['hasForeignInvestment'] == true", context)).isTrue();

    BusinessMetaContext falseContext = buildContext(Map.of("hasForeignInvestment", false));
    assertThat(gateway.evaluate("facts['hasForeignInvestment'] == true", falseContext)).isFalse();
  }

  @Test
  @DisplayName("facts 数值比较:facts['headcount'] > 10")
  void evaluate_contextFactsMap_numericComparison() {
    BusinessMetaContext context = buildContext(Map.of("headcount", 20));
    assertThat(gateway.evaluate("facts['headcount'] > 10", context)).isTrue();

    BusinessMetaContext smallContext = buildContext(Map.of("headcount", 5));
    assertThat(gateway.evaluate("facts['headcount'] > 10", smallContext)).isFalse();
  }

  @Test
  @DisplayName("纯 facts Map 降维求值:明细层场景只注入 facts 变量")
  void evaluate_pureFactsMap_worksForDetailLevel() {
    Map<String, Object> pureFacts = Map.of("count", 20, "active", true);
    assertThat(gateway.evaluate("facts['count'] > 10", pureFacts)).isTrue();
    assertThat(gateway.evaluate("facts['active'] == true", pureFacts)).isTrue();
    assertThat(gateway.evaluate("facts['count'] > 100", pureFacts)).isFalse();
  }

  @Test
  @DisplayName("非法表达式容错:返回 false 而非抛异常")
  void evaluate_invalidExpression_returnsFalse() {
    BusinessMetaContext context = buildContext(Map.of());
    assertThat(gateway.evaluate(">>> invalid syntax <<<", context)).isFalse();
    assertThat(gateway.evaluate("undefinedVar.undefinedField", context)).isFalse();
  }

  @Test
  @DisplayName("表达式缓存:相同表达式重复求值结果一致")
  void evaluate_cachedExpression_consistentResult() {
    BusinessMetaContext context = buildContext(Map.of("headcount", 20));
    String expr = "facts['headcount'] > 10";

    // 首次求值会触发编译并缓存
    assertThat(gateway.evaluate(expr, context)).isTrue();
    // 再次求值应命中缓存,结果一致
    assertThat(gateway.evaluate(expr, context)).isTrue();

    // 不同的上下文相同表达式也应正常工作
    BusinessMetaContext smallContext = buildContext(Map.of("headcount", 5));
    assertThat(gateway.evaluate(expr, smallContext)).isFalse();
  }

  /**
   * 构建测试用 BusinessMetaContext
   */
  private BusinessMetaContext buildContext(Map<String, Object> extensionFacts) {
    return new BusinessMetaContext(
      CustomerNo.of("C-001"),
      ProductNo.of("P-001"),
      OperationModel.Single_Trustee,
      PlanNo.of("PL-001"),
      BusinessType.ACC_PLAN_CREATE,
      AccountManager.CJP,
      extensionFacts
    );
  }
}
