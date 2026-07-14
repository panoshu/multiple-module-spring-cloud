package com.example.file.domain.model.rule;

// 日期规则 (支持绝对时间限制，以及相对另一个字段的跨字段比较)
public record DateRule(
  String minDate,        // 绝对最小日期 (如 1900-01-01)
  String maxDate,        // 绝对最大日期
  String beforeField,    // 跨字段校验：必须早于指定的 jsonKey (解决开始时间<=结束时间)
  String afterField      // 跨字段校验：必须晚于指定的 jsonKey
) implements ValidationRule {
}
