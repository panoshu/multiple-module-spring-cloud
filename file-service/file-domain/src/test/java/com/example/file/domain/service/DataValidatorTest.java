package com.example.file.domain.service;

import com.example.file.domain.gateway.ExpressionEvaluator;
import com.example.file.domain.model.enums.ErrorPolicy;
import com.example.file.domain.model.enums.FieldType;
import com.example.file.domain.model.valueobject.ValidationResult;
import com.example.file.domain.model.valueobject.config.ValidationRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataValidatorTest {

  @Test
  void should_collect_one_error_with_fail_fast_policy() {
    ValidationRule rule1 = new ValidationRule("qty", null, "qty > 0", "数量必须大于0", FieldType.INTEGER);
    ValidationRule rule2 = new ValidationRule("price", null, "price >= 0", "价格不能为负", FieldType.DECIMAL);
    ExpressionEvaluator evaluator = (expr, env) -> switch (expr) {
      case "qty > 0" -> ((Number) env.get("qty")).doubleValue() > 0;
      case "price >= 0" -> ((Number) env.get("price")).doubleValue() >= 0;
      default -> throw new IllegalArgumentException(expr);
    };
    DataValidator validator = new DataValidator();
    Map<String, Object> data = Map.of("qty", -1, "price", 5.0);

    ValidationResult result = validator.validate(data, List.of(rule1, rule2), ErrorPolicy.FAIL_FAST, evaluator);

    assertThat(result.passed()).isFalse();
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().get(0).field()).isEqualTo("qty");
  }

  @Test
  void should_collect_all_errors_with_collect_all_policy() {
    ValidationRule r1 = new ValidationRule("qty", null, "qty > 0", "数量必须大于0", FieldType.INTEGER);
    ValidationRule r2 = new ValidationRule("price", null, "price >= 0", "价格不能为负", FieldType.DECIMAL);
    ExpressionEvaluator evaluator = (expr, env) -> switch (expr) {
      case "qty > 0" -> ((Number) env.get("qty")).doubleValue() > 0;
      case "price >= 0" -> ((Number) env.get("price")).doubleValue() >= 0;
      default -> throw new IllegalArgumentException(expr);
    };
    DataValidator validator = new DataValidator();
    Map<String, Object> data = Map.of("qty", -1, "price", -5.0);

    ValidationResult result = validator.validate(data, List.of(r1, r2), ErrorPolicy.COLLECT_ALL, evaluator);

    assertThat(result.passed()).isFalse();
    assertThat(result.errors()).hasSize(2);
  }
}
