package com.example.core.adapter.validator;

import com.example.core.api.registrar.BusinessTypeRegistrar;
import com.example.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SupportedBusinessTypeValidator 单元测试
 *
 * @author panoshu
 */
class SupportedBusinessTypeValidatorTest {

  @Test
  void should_pass_when_type_supported() {
    BusinessTypeRegistrar registrar = BusinessTypeRegistrar.of("ANNUITY_OPEN", "ANNUITY_CHANGE");
    SupportedBusinessTypeValidator validator = new SupportedBusinessTypeValidator(registrar);
    assertThatCode(() -> validator.validate("ANNUITY_OPEN")).doesNotThrowAnyException();
  }

  @Test
  void should_fail_when_type_not_supported() {
    BusinessTypeRegistrar registrar = BusinessTypeRegistrar.of("ANNUITY_OPEN");
    SupportedBusinessTypeValidator validator = new SupportedBusinessTypeValidator(registrar);
    assertThatThrownBy(() -> validator.validate("APPROVAL_FLOW"))
      .isInstanceOf(BusinessException.class)
      .extracting(ex -> ((BusinessException) ex).displayMessage())
      .asString()
      .contains("不支持的业务类型");
  }

  @Test
  void should_fail_when_registrar_missing() {
    SupportedBusinessTypeValidator validator = new SupportedBusinessTypeValidator(null);
    assertThatThrownBy(() -> validator.validate("ANNUITY_OPEN"))
      .isInstanceOf(BusinessException.class)
      .extracting(ex -> ((BusinessException) ex).displayMessage())
      .asString()
      .contains("未配置业务类型注册器");
  }
}
