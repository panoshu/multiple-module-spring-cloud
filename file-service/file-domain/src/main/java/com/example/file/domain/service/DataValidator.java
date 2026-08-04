package com.example.file.domain.service;

import com.example.file.domain.gateway.ExpressionEvaluator;
import com.example.file.domain.model.enums.ErrorPolicy;
import com.example.file.domain.model.valueobject.ValidationError;
import com.example.file.domain.model.valueobject.ValidationResult;
import com.example.file.domain.model.valueobject.config.ValidationRule;
import com.example.shared.domain.annotation.DomainService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@DomainService
public class DataValidator {

  public ValidationResult validate(Map<String, Object> data, List<ValidationRule> rules,
                                   ErrorPolicy policy, ExpressionEvaluator evaluator) {
    List<ValidationError> errors = new ArrayList<>();
    for (ValidationRule rule : rules) {
      try {
        Object ok = evaluator.evaluate(rule.expr(), data);
        if (!Boolean.TRUE.equals(ok)) {
          errors.add(new ValidationError(rule.field(), rule.message(), rule.expr()));
          if (policy == ErrorPolicy.FAIL_FAST) break;
        }
      } catch (Exception ex) {
        errors.add(new ValidationError(rule.field(), "表达式执行异常: " + ex.getMessage(), rule.expr()));
        if (policy == ErrorPolicy.FAIL_FAST) break;
      }
    }
    return new ValidationResult(errors);
  }
}
