package com.example.file.domain.service;

import com.example.file.domain.gateway.ExpressionEvaluator;
import com.example.file.domain.model.enums.FieldType;
import com.example.file.domain.model.valueobject.config.DerivationRule;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataDeriverTest {

  @Test
  void should_derive_total_from_qty_and_price() {
    DerivationRule rule = new DerivationRule("total", "qty * price", FieldType.DECIMAL, "计算总价");
    ExpressionEvaluator evaluator = (expr, env) -> {
      if ("qty * price".equals(expr)) {
        Number qty = (Number) env.get("qty");
        Number price = (Number) env.get("price");
        return qty.doubleValue() * price.doubleValue();
      }
      throw new IllegalArgumentException("Unknown expr: " + expr);
    };
    DataDeriver deriver = new DataDeriver();
    Map<String, Object> data = Map.of("qty", 5, "price", 10.0);

    Map<String, Object> result = deriver.derive(data, List.of(rule), evaluator);

    assertThat(result).containsEntry("total", 50.0);
    assertThat(result).containsEntry("qty", 5);
  }
}
