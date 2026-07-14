package com.example.file.domain.model.rule;

import java.math.BigDecimal;

// 数值规则 (最大最小值)
public record NumberRule(
  BigDecimal min,
  BigDecimal max
) implements ValidationRule {
}
