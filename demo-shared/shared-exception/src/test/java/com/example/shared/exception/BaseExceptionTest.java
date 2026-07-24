package com.example.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link BaseException} 契约测试。
 * <p>
 * 验证基础异常类的抽象性、构造函数可见性、链式 API 行为及线程安全视图契约。
 *
 * @author Trae
 * @since 2026/07/24
 */
@DisplayName("BaseException 契约测试")
class BaseExceptionTest {

  /**
   * 自定义测试用 ErrorDefinition，避免与 CommonError 耦合。
   */
  private enum TestError implements ErrorDefinition {
    SAMPLE("99001", "测试错误");

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

  @Nested
  @DisplayName("抽象类契约")
  class AbstractClassContract {

    @Test
    @DisplayName("BaseException 必须声明为 abstract，禁止直接实例化")
    void shouldBeAbstractClass() {
      assertThat(Modifier.isAbstract(BaseException.class.getModifiers()))
          .as("BaseException 应为抽象类，业务方只能实例化其子类（Domain/Business/SystemException）")
          .isTrue();
    }

    @Test
    @DisplayName("BaseException 所有构造函数必须为 protected，禁止外部直接 new")
    void shouldHaveProtectedConstructorsOnly() {
      Constructor<?>[] constructors = BaseException.class.getDeclaredConstructors();
      assertThat(constructors).isNotEmpty();
      for (Constructor<?> constructor : constructors) {
        assertThat(Modifier.isProtected(constructor.getModifiers()))
            .as("构造函数 %s 必须为 protected", constructor)
            .isTrue();
      }
    }
  }

  @Nested
  @DisplayName("子类实例化")
  class SubclassInstantiation {

    @Test
    @DisplayName("BusinessException 可正常创建并携带 ErrorDefinition")
    void businessExceptionShouldCarryError() {
      BusinessException exception = new BusinessException(TestError.SAMPLE);
      assertThat(exception.code()).isEqualTo("99001");
      assertThat(exception.getMessage()).isEqualTo("[99001] 测试错误");
    }

    @Test
    @DisplayName("SystemException 可正常创建并携带原因")
    void systemExceptionShouldCarryCause() {
      Throwable cause = new RuntimeException("底层故障");
      SystemException exception = new SystemException(TestError.SAMPLE, cause);
      assertThat(exception.getCause()).isSameAs(cause);
      assertThat(exception.code()).isEqualTo("99001");
    }

    @Test
    @DisplayName("DomainException 不应填充堆栈，提升性能")
    void domainExceptionShouldNotFillStackTrace() {
      DomainException exception = new DomainException(TestError.SAMPLE);
      assertThat(exception.getStackTrace()).isEmpty();
    }
  }

  @Nested
  @DisplayName("链式 API 与上下文")
  class ChainingApi {

    @Test
    @DisplayName("withUserDetail 应链式返回并影响 displayMessage")
    void withUserDetailShouldAppendToDisplayMessage() {
      BusinessException exception = new BusinessException(TestError.SAMPLE)
          .withUserDetail("当前库存为 5");
      assertThat(exception.displayMessage()).isEqualTo("测试错误，当前库存为 5");
    }

    @Test
    @DisplayName("userDetail 为空时 displayMessage 应只返回标准话术")
    void displayMessageShouldFallbackToStandardWhenUserDetailBlank() {
      BusinessException exception = new BusinessException(TestError.SAMPLE);
      assertThat(exception.displayMessage()).isEqualTo("测试错误");
    }

    @Test
    @DisplayName("withLogDetail 应链式返回并出现在 logMessage")
    void withLogDetailShouldAppendToLogMessage() {
      SystemException exception = new SystemException(TestError.SAMPLE)
          .withLogDetail("Redis 连接超时");
      assertThat(exception.logMessage()).contains("LogDetail: [Redis 连接超时]");
    }

    @Test
    @DisplayName("withContext 应链式返回并出现在 logContext 只读视图")
    void withContextShouldPopulateLogContext() {
      SystemException exception = new SystemException(TestError.SAMPLE)
          .withContext("userId", "U001")
          .withContext("orderId", "O001");
      assertThat(exception.getLogContext())
          .containsEntry("userId", "U001")
          .containsEntry("orderId", "O001")
          .hasSize(2);
    }

    @Test
    @DisplayName("getLogContext 返回不可变视图，外部修改应抛 UnsupportedOperationException")
    void logContextShouldBeUnmodifiable() {
      SystemException exception = new SystemException(TestError.SAMPLE)
          .withContext("userId", "U001");
      assertThatThrownBy(() -> exception.getLogContext().put("hack", "value"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("链式调用应返回当前异常实例本身，便于继续操作")
    void chainShouldReturnSelf() {
      BusinessException original = new BusinessException(TestError.SAMPLE);
      BusinessException chained = original.withUserDetail("detail")
          .withLogDetail("log")
          .withContext("k", "v");
      assertThat(chained).isSameAs(original);
    }
  }
}
