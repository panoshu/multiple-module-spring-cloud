package com.example.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * starter / kernel 边界不变量（K-05 / K-07 / K-08 / A-04 / X-06）。
 * <p>
 * 把本轮结构整改后的边界固化为构建期规则：
 * <ul>
 *   <li>application/domain 不依赖 file/approval 的 API/类型模块（K-07/K-08）；</li>
 *   <li>file-service 的 {@code FileId} 只允许出现在 kernel infrastructure 的适配边界；</li>
 *   <li>业务服务不再继承 kernel 仓储实现（A-04）；</li>
 *   <li>kernel 仓储实现保持“非 Spring Bean”，由 starter 统一装配。</li>
 * </ul>
 */
@DisplayName("starter / kernel 边界不变量（X-06）")
class KernelStarterBoundaryTest {

  private static final String KERNEL_DOMAIN = "com.example.core.domain..";
  private static final String KERNEL_APPLICATION = "com.example.core.application..";
  private static final String KERNEL_ADAPTER = "com.example.core.adapter..";
  private static final String KERNEL_INFRASTRUCTURE = "com.example.core.infrastructure..";
  private static final String KERNEL_STARTER = "com.example.core.starter..";
  private static final String ANNUITY = "com.example.annuity..";

  private static final String[] FILE_APPROVAL_CONTRACTS = {
    "com.example.file.api..", "com.example.file.types..",
    "com.example.approval.api..", "com.example.approval.types.."
  };

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes = new ClassFileImporter().importPackages(
      "com.example.core", "com.example.annuity", "com.example.file", "com.example.approval");
    assertThat(classes.size()).isGreaterThan(500);
    assertThat(hasClass("com.example.core.starter.BusinessCoreStarterAutoConfiguration"))
      .as("starter 装配入口必须在类路径中").isTrue();
    assertThat(hasClass("com.example.core.application.engine.inbound.FileParsedMessage"))
      .as("K-07 kernel 自有入站消息必须在类路径中").isTrue();
  }

  @Test
  @DisplayName("1. application 不依赖 file/approval API 或 types（K-07/K-08）")
  void applicationDoesNotDependOnExternalServiceContracts() {
    ArchRule rule = noClasses().that().resideInAPackage(KERNEL_APPLICATION)
      .should().dependOnClassesThat().resideInAnyPackage(FILE_APPROVAL_CONTRACTS)
      .because("application 只消费 kernel 自有入站消息与 FileRefId，外部 DTO 映射在 adapter 层");
    rule.check(classes);
  }

  @Test
  @DisplayName("2. domain 不依赖 file/approval API 或 types（K-08）")
  void domainDoesNotDependOnExternalServiceContracts() {
    ArchRule rule = noClasses().that().resideInAPackage(KERNEL_DOMAIN)
      .should().dependOnClassesThat().resideInAnyPackage(FILE_APPROVAL_CONTRACTS)
      .because("domain 使用 kernel 自有 FileRefId，file-service 类型只在 infrastructure 边界出现");
    rule.check(classes);
  }

  @Test
  @DisplayName("3. file-service 的 FileId 仅允许 kernel infrastructure 引用")
  void fileServiceFileIdIsConfinedToInfrastructureBoundary() {
    ArchRule rule = noClasses().that().resideInAnyPackage(
        KERNEL_DOMAIN, KERNEL_APPLICATION, KERNEL_ADAPTER, KERNEL_STARTER)
      .should().dependOnClassesThat().haveFullyQualifiedName("com.example.file.types.FileId")
      .because("FileRefId 是 kernel 领域契约；FileId 仅由 infrastructure 在 file-service 调用处转换");
    rule.check(classes);
  }

  @Test
  @DisplayName("4. 业务服务不得继承 kernel 仓储实现（A-04）")
  void businessServicesDoNotExtendKernelRepositories() {
    ArchRule rule = noClasses().that().resideInAPackage(ANNUITY)
      .should().beAssignableTo("com.example.core.infrastructure.business.repository.AbstractBusinessRepository")
      .because("kernel 仓储由 starter 统一注册，业务服务通过接口注入，不再复制空壳子类");
    rule.check(classes);
  }

  @Test
  @DisplayName("5. kernel 仓储实现保持非 @Repository，由 starter 统一装配")
  void kernelRepositoryImplementationsAreWiredByStarter() {
    ArchRule rule = classes().that().resideInAPackage(KERNEL_INFRASTRUCTURE)
      .and().haveSimpleNameEndingWith("RepositoryImpl")
      .should().notBeAnnotatedWith("org.springframework.stereotype.Repository")
      .because("kernel 仓储不应被组件扫描直接注册；starter 用 @Bean + @ConditionalOnMissingBean 装配");
    rule.check(classes);
  }

  @Test
  @DisplayName("6. starter 声明五个仓储 Bean 且方法签名可被宿主覆盖")
  void starterDeclaresRepositoryBeans() throws Exception {
    Class<?> starter = Class.forName(
      "com.example.core.starter.BusinessCoreStarterAutoConfiguration");
    assertThat(annotationNames(starter))
      .as("starter 必须使用 @AutoConfiguration 参与自动装配")
      .contains("org.springframework.boot.autoconfigure.AutoConfiguration");

    Set<String> beanReturnTypes = Arrays.stream(starter.getDeclaredMethods())
      .filter(method -> method.isAnnotationPresent(
        org.springframework.context.annotation.Bean.class))
      .map(java.lang.reflect.Method::getName)
      .collect(Collectors.toSet());
    assertThat(beanReturnTypes).contains(
      "businessApplicationRepository", "businessBatchRepository", "businessFormRepository",
      "externalInteractionRepository", "stepExecutionRepository");
  }

  @Test
  @DisplayName("7. 入站事件适配器位于 adapter 层，application 无 @EventListener 外部 DTO")
  void inboundAdapterIsInAdapterLayer() {
    assertThat(hasClass("com.example.core.adapter.inbound.ExternalEventInboundAdapter"))
      .as("外部事件 DTO 的映射入口应在 adapter 层").isTrue();

    for (JavaClass clazz : classes) {
      if (!clazz.getPackageName().startsWith("com.example.core.application")) {
        continue;
      }
      for (JavaMethod method : clazz.getMethods()) {
        if (!method.isAnnotatedWith(org.springframework.context.event.EventListener.class)) {
          continue;
        }
        for (JavaClass parameterType : method.getRawParameterTypes()) {
          String name = parameterType.getName();
          assertThat(name)
            .as("application @EventListener 不得直接消费外部事件 DTO：%s#%s",
              clazz.getSimpleName(), method.getName())
            .doesNotStartWith("com.example.file.api.event")
            .doesNotStartWith("com.example.approval.api.event");
        }
      }
    }
  }

  private static boolean hasClass(String fullyQualifiedName) {
    for (JavaClass clazz : classes) {
      if (clazz.getName().equals(fullyQualifiedName)) {
        return true;
      }
    }
    return false;
  }

  private static Set<String> annotationNames(Class<?> type) {
    return Arrays.stream(type.getAnnotations())
      .map(Annotation::annotationType)
      .map(Class::getName)
      .collect(Collectors.toSet());
  }
}
