package com.example.core.infrastructure.engine.evaluation;

import com.example.core.domain.business.aggregate.valueobject.business.AccountManager;
import com.example.core.domain.business.aggregate.valueobject.business.BusinessType;
import com.example.core.domain.engine.aggregate.valueobject.BusinessMetaContext;
import com.example.core.types.CustomerNo;
import com.example.core.types.OperatingMode;
import com.example.core.types.PlanNo;
import com.example.core.types.ProductNo;
import com.example.shared.exception.SystemException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SpelConditionEvaluationGateway 条件求值测试")
class SpelConditionEvaluationGatewayTest {

  private SpelConditionEvaluationGateway gateway;

  @BeforeEach
  void setUp() {
    gateway = new SpelConditionEvaluationGateway();
  }

  @Test
  @DisplayName("空表达式短路返回 true")
  void blankExpression_returnsTrue() {
    BusinessMetaContext context = context(Map.of());
    assertThat(gateway.evaluate(null, context)).isTrue();
    assertThat(gateway.evaluate("", context)).isTrue();
    assertThat(gateway.evaluate("   ", context)).isTrue();
    assertThat(gateway.evaluate(null, Map.of())).isTrue();
  }

  @Test
  @DisplayName("上下文与纯 facts 重载均可正常求值")
  void validExpression_evaluates() {
    BusinessMetaContext context = context(Map.of("headcount", 20));

    assertThat(gateway.evaluate("#facts['headcount'] > 10", context)).isTrue();
    assertThat(gateway.evaluate("#facts['headcount'] > 100", context)).isFalse();
    assertThat(gateway.evaluate("#facts['count'] > 10", Map.of("count", 20))).isTrue();
  }

  @Test
  @DisplayName("非法表达式显式失败,不静默返回 false")
  void invalidExpression_throwsSystemException() {
    BusinessMetaContext context = context(Map.of());

    assertThatThrownBy(() -> gateway.evaluate("#facts['x'] > &&", context))
      .isInstanceOf(SystemException.class);
    assertThatThrownBy(() -> gateway.evaluate("#undefinedVar.field", context))
      .isInstanceOf(SystemException.class);
  }

  @Test
  @DisplayName("纯 facts 重载的非法表达式同样显式失败")
  void invalidExpressionInPureFacts_throwsSystemException() {
    assertThatThrownBy(() -> gateway.evaluate("#facts['x'] > &&", Map.of()))
      .isInstanceOf(SystemException.class);
  }

  private BusinessMetaContext context(Map<String, Object> facts) {
    return new BusinessMetaContext(
      CustomerNo.of("C-001"),
      ProductNo.of("P-001"),
      OperatingMode.TRUSTEE_ONLY,
      PlanNo.of("PL-001"),
      BusinessType.ACC_PLAN_CREATE,
      AccountManager.CJP,
      facts
    );
  }
}
