package com.example.file.domain.model.rule;

// 1. 基础校验规则族
public sealed interface ValidationRule permits StringRule, NumberRule, DateRule {
}
