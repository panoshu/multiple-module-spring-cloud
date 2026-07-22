package com.example.core.domain.business.aggregate.valueobject;

import com.example.shared.domain.aggregate.valueobject.ValueObject;

/**
 * 校验结果值对象, 校验器执行校验步骤应当返回此类型
 *
 * @author <a href="mailto: hup@cj-pension.com.cn">hupan</a>
 * @version 1.0
 * @since 2026/5/13 18:44
 */
public interface ValidationResult extends ValueObject {

  static ValidationResult passed() {
    return DefaultValidationResult.PASSED;
  }

  static ValidationResult failed(String errorCode, String errorMessage) {
    return new DefaultValidationResult(false, errorCode, errorMessage);
  }

  /**
   * 校验是否通过
   */
  boolean isPassed();

  /**
   * 错误码 (校验不通过时返回)
   */
  String errorCode();

  /**
   * 错误信息 (校验不通过时返回)
   */
  String errorMessage();

  final record DefaultValidationResult(
    boolean isPassed,
    String errorCode,
    String errorMessage
  ) implements ValidationResult {
    private static final DefaultValidationResult PASSED = new DefaultValidationResult(true, null, null);

    public DefaultValidationResult(boolean isPassed, String errorCode, String errorMessage) {
      this.isPassed = isPassed;
      this.errorCode = errorCode;
      this.errorMessage = errorMessage;
    }
  }
}
