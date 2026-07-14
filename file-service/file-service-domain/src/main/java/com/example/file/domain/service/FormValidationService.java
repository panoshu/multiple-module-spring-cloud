package com.example.file.domain.service;

import com.example.file.domain.model.rule.DateRule;
import com.example.file.domain.model.rule.NumberRule;
import com.example.file.domain.model.rule.StringRule;
import com.example.file.domain.model.rule.ValidationRule;
import com.example.file.domain.model.schema.DataRow;
import com.example.file.domain.model.schema.FieldConfig;
import com.example.file.domain.model.schema.ValidationError;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

public class FormValidationService {

  public void validateRow(DataRow row, List<FieldConfig> configs) {
    for (FieldConfig config : configs) {
      Object value = row.data().get(config.jsonKey());

      // 1. 必填项校验
      if (config.required() && (value == null || value.toString().isBlank())) {
        row.errors().add(new ValidationError(row.rowIndex(), config.jsonKey(), "不能为空", null));
        continue;
      }
      if (value == null || value.toString().isBlank()) {
        continue;
      }

      String strVal = value.toString().trim();

      // 2. 规则校验 (利用 JDK 25 的模式匹配)
      for (ValidationRule rule : config.rules()) {
        switch (rule) {
          case StringRule sr -> {
            if (sr.minLength() != null && strVal.length() < sr.minLength()) {
              row.errors().add(new ValidationError(row.rowIndex(), config.jsonKey(), "长度不能小于 " + sr.minLength(), null));
            }
            if (sr.maxLength() != null && strVal.length() > sr.maxLength()) {
              row.errors().add(new ValidationError(row.rowIndex(), config.jsonKey(), "长度不能大于 " + sr.maxLength(), null));
            }
            if (sr.pattern() != null && !strVal.matches(sr.pattern())) {
              row.errors().add(new ValidationError(row.rowIndex(), config.jsonKey(), "格式不正确", null));
            }
          }
          case NumberRule nr -> {
            try {
              BigDecimal num = new BigDecimal(strVal);
              if (nr.min() != null && num.compareTo(nr.min()) < 0) {
                row.errors().add(new ValidationError(row.rowIndex(), config.jsonKey(), "数值不能小于 " + nr.min(), null));
              }
              if (nr.max() != null && num.compareTo(nr.max()) > 0) {
                row.errors().add(new ValidationError(row.rowIndex(), config.jsonKey(), "数值不能大于 " + nr.max(), null));
              }
            } catch (NumberFormatException e) {
              row.errors().add(new ValidationError(row.rowIndex(), config.jsonKey(), "必须是有效的数字", null));
            }
          }
          case DateRule dr -> {
            try {
              LocalDate date = LocalDate.parse(strVal); // 假设日期已被转换为 yyyy-MM-dd

              // 绝对日期校验
              if (dr.minDate() != null && date.isBefore(LocalDate.parse(dr.minDate()))) {
                row.errors().add(new ValidationError(row.rowIndex(), config.jsonKey(), "日期不能早于 " + dr.minDate(), null));
              }
              if (dr.maxDate() != null && date.isAfter(LocalDate.parse(dr.maxDate()))) {
                row.errors().add(new ValidationError(row.rowIndex(), config.jsonKey(), "日期不能晚于 " + dr.maxDate(), null));
              }

              // 跨字段校验：例如当前字段(开始时间)必须早于 beforeField(结束时间)
              if (dr.beforeField() != null) {
                Object afterVal = row.data().get(dr.beforeField());
                if (afterVal != null && !afterVal.toString().isBlank()) {
                  LocalDate afterDate = LocalDate.parse(afterVal.toString().trim());
                  if (!date.isBefore(afterDate)) {
                    row.errors().add(new ValidationError(row.rowIndex(), config.jsonKey(), "必须早于 " + dr.beforeField(), null));
                  }
                }
              }

              // 跨字段校验：例如当前字段必须晚于 afterField
              if (dr.afterField() != null) {
                Object beforeVal = row.data().get(dr.afterField());
                if (beforeVal != null && !beforeVal.toString().isBlank()) {
                  LocalDate beforeDate = LocalDate.parse(beforeVal.toString().trim());
                  if (!date.isAfter(beforeDate)) {
                    row.errors().add(new ValidationError(row.rowIndex(), config.jsonKey(), "必须晚于 " + dr.afterField(), null));
                  }
                }
              }
            } catch (DateTimeParseException e) {
              row.errors().add(new ValidationError(row.rowIndex(), config.jsonKey(), "必须是有效的日期格式(yyyy-MM-dd)", null));
            }
          }
        }
      }
    }
  }
}
