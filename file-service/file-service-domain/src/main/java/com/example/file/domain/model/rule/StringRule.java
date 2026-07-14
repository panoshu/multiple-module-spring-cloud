package com.example.file.domain.model.rule;

// 字符串规则 (长度限制、正则)
public record StringRule(
  Integer minLength,
  Integer maxLength,
  String pattern
) implements ValidationRule {
}
