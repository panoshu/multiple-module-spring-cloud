package com.example.core.infrastructure.engine.evaluation;

import com.example.core.domain.engine.gateway.ConditionEvaluationGateway;
import com.example.shared.exception.SystemException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 条件表达式语法预校验契约（spec §7.1，批次 34）。
 * <p>
 * 三个求值引擎对「合法表达式通过、空白视为无条件、语法非法显式失败」的行为必须一致；
 * 发布期只做语法检查，不注入运行时事实、不求值。
 */
@DisplayName("条件表达式语法预校验契约")
class ConditionValidationContractTest {

  static Stream<Arguments> gateways() {
    return Stream.of(
      Arguments.of(new SpelConditionEvaluationGateway(),
        "#facts['headcount'] > 10", "#facts['x'] > &&"),
      Arguments.of(new AviatorConditionEvaluationGateway(),
        "facts['headcount'] > 10", "facts['x'] > &&"),
      Arguments.of(new QLExpressConditionEvaluationGateway(),
        "facts['headcount'] > 10", "facts['x'] > &&"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("gateways")
  @DisplayName("合法表达式通过，空白表达式视为无条件短路")
  void validate_acceptsValidAndBlankExpressions(ConditionEvaluationGateway gateway,
                                               String validExpression, String invalidExpression) {
    assertThatCode(() -> gateway.validate(validExpression)).doesNotThrowAnyException();
    assertThatCode(() -> gateway.validate(null)).doesNotThrowAnyException();
    assertThatCode(() -> gateway.validate("   ")).doesNotThrowAnyException();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("gateways")
  @DisplayName("语法非法表达式显式失败，不静默放行")
  void validate_rejectsInvalidSyntax(ConditionEvaluationGateway gateway,
                                     String validExpression, String invalidExpression) {
    assertThatThrownBy(() -> gateway.validate(invalidExpression))
      .isInstanceOf(SystemException.class);
  }
}
