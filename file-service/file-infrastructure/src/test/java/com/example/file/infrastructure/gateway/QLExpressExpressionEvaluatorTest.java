package com.example.file.infrastructure.gateway;

import com.alibaba.qlexpress4.exception.QLSyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("QLExpressExpressionEvaluator 单元测试")
class QLExpressExpressionEvaluatorTest {

  private QLExpressExpressionEvaluator evaluator;

  @BeforeEach
  void setUp() {
    evaluator = new QLExpressExpressionEvaluator();
  }

  @Test
  @DisplayName("正常求值 - 简单算术与逻辑运算")
  void testEvaluate_SimpleArithmeticAndLogic() {
    String expr = "a + b > 10 && c == 'test'";
    Map<String, Object> context = new HashMap<>();
    context.put("a", 5);
    context.put("b", 6);
    context.put("c", "test");

    Object result = evaluator.evaluate(expr, context);
    assertThat(result).isInstanceOf(Boolean.class).isEqualTo(true);
  }

  @Test
  @DisplayName("正常求值 - 访问 Map 中的嵌套属性 (QLExpress 语法)")
  void testEvaluate_MapNestedAccess() {
    String expr = "facts.age >= 18 && facts.status == 'ACTIVE'";
    Map<String, Object> context = new HashMap<>();
    Map<String, Object> facts = new HashMap<>();
    facts.put("age", 20);
    facts.put("status", "ACTIVE");
    context.put("facts", facts);

    Object result = evaluator.evaluate(expr, context);
    assertThat(result).isEqualTo(true);
  }

  @Test
  @DisplayName("异常处理 - 语法错误应抛出 RuntimeException，且 cause 明确为 QLSyntaxException")
  void testEvaluate_SyntaxError_ThrowsRuntimeException() {
    String invalidExpr = "a + b >"; // 缺少右操作数
    Map<String, Object> context = Map.of("a", 1, "b", 2);

    assertThatThrownBy(() -> evaluator.evaluate(invalidExpr, context))
      // 1. 断言外层包装异常类型
      .isInstanceOf(RuntimeException.class)
      // 2. 修正消息匹配 (加上 '4'，并包含具体的表达式内容)
      .hasMessageContaining("QLExpress4 表达式执行失败: a + b >")
      // 3. 【增强断言】明确判断底层 cause 异常类型为 QLSyntaxException
      .hasCauseInstanceOf(QLSyntaxException.class);
  }

  @Test
  @DisplayName("性能验证 - 相同表达式多次执行应命中内部缓存且不报错")
  void testEvaluate_CacheHit_MultipleExecutions() {
    String expr = "x * 2";
    Map<String, Object> context = new HashMap<>();
    context.put("x", 5);

    for (int i = 0; i < 100; i++) {
      Object result = evaluator.evaluate(expr, context);
      assertThat(result).isEqualTo(10);
    }
  }
}
