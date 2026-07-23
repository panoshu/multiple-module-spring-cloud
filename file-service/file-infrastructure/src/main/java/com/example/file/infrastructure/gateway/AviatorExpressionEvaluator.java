package com.example.file.infrastructure.gateway;

import com.example.file.domain.gateway.ExpressionEvaluator;
import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "file.expression.engine", havingValue = "aviator", matchIfMissing = true)
public class AviatorExpressionEvaluator implements ExpressionEvaluator {

  private final AviatorEvaluatorInstance engine;

  public AviatorExpressionEvaluator() {
    this.engine = AviatorEvaluator.getInstance();
  }

  @Override
  public Object evaluate(String expr, Map<String, Object> context) {
    return engine.execute(expr, context);
  }
}
