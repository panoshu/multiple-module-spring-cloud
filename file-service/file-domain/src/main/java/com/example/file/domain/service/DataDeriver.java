package com.example.file.domain.service;

import com.example.file.domain.gateway.ExpressionEvaluator;
import com.example.file.domain.model.valueobject.config.DerivationRule;
import com.example.shared.domain.annotation.DomainService;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@DomainService
public class DataDeriver {

  public Map<String, Object> derive(Map<String, Object> data, List<DerivationRule> rules,
                                    ExpressionEvaluator evaluator) {
    Map<String, Object> result = new LinkedHashMap<>(data);
    for (DerivationRule rule : rules) {
      Object value = evaluator.evaluate(rule.expr(), result);
      result.put(rule.field(), value);
    }
    return result;
  }
}
