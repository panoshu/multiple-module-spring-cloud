package com.example.file.domain.gateway;

import java.util.Map;

public interface ExpressionEvaluator {
  Object evaluate(String expr, Map<String, Object> context);
}
