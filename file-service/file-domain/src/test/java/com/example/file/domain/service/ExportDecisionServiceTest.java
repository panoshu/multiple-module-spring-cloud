package com.example.file.domain.service;

import com.example.file.domain.gateway.ExcelExporter;
import com.example.file.domain.model.enums.FieldType;
import com.example.file.domain.model.enums.ValidationScope;
import com.example.file.domain.model.valueobject.SplitUnit;
import com.example.file.domain.model.valueobject.ValidationError;
import com.example.file.domain.model.valueobject.ValidationResult;
import com.example.file.domain.model.valueobject.config.ValidationRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ExportDecisionService 单元测试.
 *
 * <p>验证领域服务的核心决策逻辑：仅当 ValidationResult.isValid() == true 时才调用 exporter.export()。
 * 使用 spy ExcelExporter（lambda）记录调用次数和参数，不依赖任何基础设施。
 */
@DisplayName("ExportDecisionService 决策逻辑")
class ExportDecisionServiceTest {

  @Test
  @DisplayName("校验通过时调用 exporter 导出 SplitUnit")
  void should_export_when_validation_passed() {
    // given: 校验通过 + spy exporter
    SplitUnit unit = new SplitUnit("身份证", Map.of("customerNo", "000234"));
    ValidationResult validResult = ValidationResult.empty();
    InputStream template = new ByteArrayInputStream(new byte[]{});
    OutputStream out = new ByteArrayOutputStream();

    AtomicInteger callCount = new AtomicInteger(0);
    AtomicReference<SplitUnit> capturedUnit = new AtomicReference<>();
    ExcelExporter spyExporter = (u, t, o) -> {
      callCount.incrementAndGet();
      capturedUnit.set(u);
    };

    ExportDecisionService service = new ExportDecisionService();

    // when
    service.exportIfValid(unit, validResult, spyExporter, template, out);

    // then
    assertThat(callCount.get()).isEqualTo(1);
    assertThat(capturedUnit.get()).isSameAs(unit);
  }

  @Test
  @DisplayName("校验失败时不调用 exporter")
  void should_not_export_when_validation_failed() {
    // given: 校验失败 (idNo 为空)
    SplitUnit unit = new SplitUnit("身份证", Map.of("seq", "1"));
    ValidationResult invalidResult = new ValidationResult(List.of(
      new ValidationError("idNo", "证件编号不能为空", "idNo != null")));
    InputStream template = new ByteArrayInputStream(new byte[]{});
    OutputStream out = new ByteArrayOutputStream();

    AtomicInteger callCount = new AtomicInteger(0);
    ExcelExporter spyExporter = (u, t, o) -> callCount.incrementAndGet();

    ExportDecisionService service = new ExportDecisionService();

    // when
    service.exportIfValid(unit, invalidResult, spyExporter, template, out);

    // then: 决策逻辑在 service 内部，spyExporter 不被调用证明了 service 的 if 判断正确
    assertThat(callCount.get()).isZero();
  }

  @Test
  @DisplayName("ValidationResult 为 null 时抛 IllegalArgumentException")
  void should_throw_when_result_is_null() {
    SplitUnit unit = new SplitUnit("default", Map.of());
    ExcelExporter spyExporter = (u, t, o) -> {
    };

    ExportDecisionService service = new ExportDecisionService();

    assertThatThrownBy(() ->
      service.exportIfValid(unit, null, spyExporter,
        new ByteArrayInputStream(new byte[]{}),
        new ByteArrayOutputStream()))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("ValidationResult");
  }

  @Test
  @DisplayName("校验失败时返回的 ValidationError 包含正确的字段和消息")
  void should_preserve_validation_errors_in_result() {
    // 验证 ExportDecisionService 不修改 ValidationResult，决策仅基于 isValid()
    SplitUnit unit = new SplitUnit("default", Map.of());
    ValidationRule rule = new ValidationRule("idNo", ValidationScope.ROW,
      "idNo != null", "证件编号不能为空", FieldType.STRING);
    ValidationResult result = new ValidationResult(List.of(
      new ValidationError(rule.field(), rule.message(), rule.expr())));

    AtomicInteger callCount = new AtomicInteger(0);
    ExportDecisionService service = new ExportDecisionService();

    service.exportIfValid(unit, result, spyNoOp(callCount),
      new ByteArrayInputStream(new byte[]{}),
      new ByteArrayOutputStream());

    assertThat(callCount.get()).isZero();
    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).hasSize(1);
    assertThat(result.errors().get(0).field()).isEqualTo("idNo");
    assertThat(result.errors().get(0).message()).isEqualTo("证件编号不能为空");
  }

  private ExcelExporter spyNoOp(AtomicInteger callCount) {
    return (u, t, o) -> callCount.incrementAndGet();
  }
}
