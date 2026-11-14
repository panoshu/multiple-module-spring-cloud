package com.example.architecture;

import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3-2 复核落地（批次 52）：kernel adapter 层的 {@code @ConditionalOnProperty} 属性命名空间统一。
 * <p>
 * 清单原记「目前无实际问题」，核实后发现 5 个 Controller 用遗留前缀 {@code core-kernel.controllers.*}，
 * 而 validator / recovery 用 {@code core.kernel.*}——同一模块两套命名空间，接入新业务服务时极易踩坑。
 * 本类把「禁止遗留前缀」固化为机器约束；方法级与类级注解都检查。防假绿：断言导入规模与被检查注解数。
 */
@DisplayName("P3-2 kernel 条件属性命名空间统一（批次 52）")
class KernelConditionalPropertyNamespaceTest {

  private static final String CONDITIONAL_ON_PROPERTY =
    "org.springframework.boot.autoconfigure.condition.ConditionalOnProperty";
  private static final String KERNEL_ADAPTER = "com.example.core.adapter..";
  private static final String LEGACY_PREFIX = "core-kernel.";

  private static JavaClasses classes;

  @BeforeAll
  static void importKernel() {
    classes = new ClassFileImporter().importPackages("com.example.core");
    // 防假绿：确认被检查包确实导入了类，且确有 @ConditionalOnProperty 注解被规则遍历
    assertThat(classes.size()).isGreaterThan(200);
    assertThat(countConditionalOnPropertyAnnotations()).isGreaterThanOrEqualTo(6L);
  }

  @Test
  @DisplayName("adapter 的 @ConditionalOnProperty 不得使用遗留 core-kernel.* 前缀")
  void conditionalOnProperty_usesCanonicalNamespace() {
    classes().that().resideInAPackage(KERNEL_ADAPTER)
      .should(USE_CANONICAL_NAMESPACE)
      .because("P3-2 复核：属性命名空间统一为 core.kernel.*（5 个 Controller 原用 core-kernel.controllers.*）")
      .check(classes);
  }

  private static final ArchCondition<JavaClass> USE_CANONICAL_NAMESPACE =
    new ArchCondition<>("use canonical core.kernel.* property namespace") {
      @Override
      public void check(JavaClass clazz, ConditionEvents events) {
        checkAnnotations(clazz, clazz.getAnnotations(), events);
        for (JavaMethod method : clazz.getMethods()) {
          checkAnnotations(clazz, method.getAnnotations(), events);
        }
      }
    };

  private static void checkAnnotations(JavaClass owner,
                                       Set<? extends JavaAnnotation<?>> annotations,
                                       ConditionEvents events) {
    for (JavaAnnotation<?> annotation : annotations) {
      if (!CONDITIONAL_ON_PROPERTY.equals(annotation.getRawType().getName())) {
        continue;
      }
      String rendered = renderProperties(annotation.getProperties());
      if (rendered.contains(LEGACY_PREFIX)) {
        events.add(SimpleConditionEvent.violated(owner,
          owner.getSimpleName() + " 的 @ConditionalOnProperty 使用遗留前缀 core-kernel.*：" + rendered));
      }
    }
  }

  /** 注解属性值可能是 String / String[] / Collection，统一渲染为可检索字符串。 */
  private static String renderProperties(Map<String, Object> properties) {
    StringBuilder rendered = new StringBuilder();
    properties.forEach((key, value) -> rendered.append(key).append('=').append(renderValue(value)).append(';'));
    return rendered.toString();
  }

  private static String renderValue(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof Object[] array) {
      return Arrays.deepToString(array);
    }
    return value.toString();
  }

  private static long countConditionalOnPropertyAnnotations() {
    long count = 0;
    for (JavaClass clazz : classes) {
      if (!clazz.getPackageName().startsWith("com.example.core.adapter")) {
        continue;
      }
      count += countMatching(clazz.getAnnotations());
      for (JavaMethod method : clazz.getMethods()) {
        count += countMatching(method.getAnnotations());
      }
    }
    return count;
  }

  private static long countMatching(Set<? extends JavaAnnotation<?>> annotations) {
    return annotations.stream()
      .filter(annotation -> CONDITIONAL_ON_PROPERTY.equals(annotation.getRawType().getName()))
      .count();
  }
}
