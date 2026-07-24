package com.example.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DomainException}、{@link BusinessException}、{@link SystemException} 三种具体异常的契约测试。
 * <p>
 * 验证它们都继承自 {@link BaseException}，并保留各自的差异化行为。
 *
 * @author Trae
 * @since 2026/07/24
 */
@DisplayName("具体异常类型契约测试")
class SpecificExceptionTest {

  private enum TestError implements ErrorDefinition {
    DOMAIN_FAIL("TEST.0001", "领域规则失败"),
    BUSINESS_FAIL("TEST.0002", "业务规则失败"),
    SYSTEM_FAIL("TEST.0003", "系统错误");

    private final String code;
    private final String message;

    TestError(String code, String message) {
      this.code = code;
      this.message = message;
    }

    @Override
    public String code() {
      return code;
    }

    @Override
    public String message() {
      return message;
    }
  }

  @Test
  @DisplayName("DomainException 必须继承 BaseException")
  void domainExceptionShouldExtendBaseException() {
    DomainException exception = new DomainException(TestError.DOMAIN_FAIL);
    assertThat(exception).isInstanceOf(BaseException.class);
  }

  @Test
  @DisplayName("BusinessException 必须继承 BaseException")
  void businessExceptionShouldExtendBaseException() {
    BusinessException exception = new BusinessException(TestError.BUSINESS_FAIL);
    assertThat(exception).isInstanceOf(BaseException.class);
  }

  @Test
  @DisplayName("SystemException 必须继承 BaseException")
  void systemExceptionShouldExtendBaseException() {
    SystemException exception = new SystemException(TestError.SYSTEM_FAIL);
    assertThat(exception).isInstanceOf(BaseException.class);
  }

  @Test
  @DisplayName("DomainException 应跳过堆栈填充以提升性能")
  void domainExceptionShouldSkipStackTrace() {
    DomainException exception = new DomainException(TestError.DOMAIN_FAIL);
    assertThat(exception.getStackTrace()).isEmpty();
  }

  @Test
  @DisplayName("BusinessException 应保留堆栈用于排查")
  void businessExceptionShouldKeepStackTrace() {
    BusinessException exception = new BusinessException(TestError.BUSINESS_FAIL);
    assertThat(exception.getStackTrace()).isNotEmpty();
  }

  @Test
  @DisplayName("SystemException 应保留堆栈用于排查")
  void systemExceptionShouldKeepStackTrace() {
    SystemException exception = new SystemException(TestError.SYSTEM_FAIL);
    assertThat(exception.getStackTrace()).isNotEmpty();
  }

  @Test
  @DisplayName("携带 cause 的异常应正确关联原因链")
  void exceptionWithCauseShouldRetainCause() {
    Throwable cause = new IllegalStateException("DB connection lost");
    SystemException exception = new SystemException(TestError.SYSTEM_FAIL, cause);
    assertThat(exception.getCause()).isSameAs(cause);
  }

  @Test
  @DisplayName("code() 应返回 ErrorDefinition 中的 code")
  void codeShouldReturnFromErrorDefinition() {
    DomainException exception = new DomainException(TestError.DOMAIN_FAIL);
    assertThat(exception.code()).isEqualTo("TEST.0001");
  }
}
