package com.example.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * §21.4 kernel 分层架构不变量（批次 41，成立子集）。
 * <p>
 * 把规格 §21.4 / 01-架构与依赖规则 / 03-领域模型约束中「现状确实成立」的分层边界
 * 固化为机器强制层，任何退化在构建期即被拦截。纯新增测试，不改生产代码。
 * <p>
 * 刻意不机器化的三条（现状与规格字面存在张力，需规格修订或大范围重构，见设计文档
 * §23.48）：ApplicationService 入参全部 CQE 化、types 层只含 ID 类型、
 * application 零 file/approval 「DTO」依赖（现状消费 {@code file.api.event.*} 事件 DTO）。
 * 本类只检查「依赖哪一层」，不因允许的跨服务 ID/事件契约误报。
 */
@DisplayName("§21.4 kernel 分层架构不变量（成立子集）")
class KernelLayerArchitectureTest {

  private static final String KERNEL_DOMAIN = "com.example.core.domain..";
  private static final String KERNEL_APPLICATION = "com.example.core.application..";
  private static final String KERNEL_ADAPTER = "com.example.core.adapter..";
  private static final String KERNEL_API = "com.example.core.api..";
  private static final String KERNEL_INFRASTRUCTURE = "com.example.core.infrastructure..";

  /** 跨服务实现层：kernel 任何层都不得依赖；允许的是 {@code *.api..} 与 {@code *.types..}。 */
  private static final String[] CROSS_SERVICE_IMPL_LAYERS = {
    "com.example.file.domain..", "com.example.file.application..",
    "com.example.file.infrastructure..", "com.example.file.adapter..",
    "com.example.approval.domain..", "com.example.approval.application..",
    "com.example.approval.infrastructure..", "com.example.approval.adapter.."
  };

  private static JavaClasses classes;

  @BeforeAll
  static void importKernelAndBusinessServiceLayers() {
    classes = new ClassFileImporter().importPackages(
      "com.example.core", "com.example.file", "com.example.approval");
    // noClasses() 空匹配会「假绿」，逐个确认被检查的 kernel 层确实导入了类
    assertThat(classes.size()).isGreaterThan(500);
    assertThat(countIn("com.example.core.domain")).isGreaterThan(50);
    assertThat(countIn("com.example.core.application")).isGreaterThan(50);
    assertThat(countIn("com.example.core.adapter")).isGreaterThan(10);
    assertThat(countIn("com.example.core.infrastructure")).isGreaterThan(30);
    assertThat(countIn("com.example.file.domain")).isGreaterThan(10);
    assertThat(countIn("com.example.approval.domain")).isGreaterThan(0);
    assertThat(countIn("com.example.approval.application")).isGreaterThan(0);
  }

  private static long countIn(String packagePrefix) {
    long count = 0;
    for (JavaClass clazz : classes) {
      if (clazz.getPackageName().startsWith(packagePrefix)) {
        count++;
      }
    }
    return count;
  }

  @Test
  @DisplayName("1. domain 不依赖 Spring / Jackson / MyBatis-Flex / JPA 等框架")
  void domainIsFrameworkFree() {
    noClasses().that().resideInAPackage(KERNEL_DOMAIN)
      .should().dependOnClassesThat().resideInAnyPackage(
        "org.springframework..", "com.fasterxml.jackson..",
        "com.mybatisflex..", "org.apache.ibatis..", "jakarta.persistence..")
      .because("03-领域模型约束：domain 层除 Lombok 外禁止依赖外部框架")
      .check(classes);
  }

  @Test
  @DisplayName("2. domain 不依赖 kernel 的 api/application/adapter/infrastructure 层")
  void domainDoesNotDependOnUpperKernelLayers() {
    noClasses().that().resideInAPackage(KERNEL_DOMAIN)
      .should().dependOnClassesThat().resideInAnyPackage(
        KERNEL_API, KERNEL_APPLICATION, KERNEL_ADAPTER, KERNEL_INFRASTRUCTURE)
      .because("01-架构与依赖规则：依赖倒置，domain 不依赖上层")
      .check(classes);
  }

  @Test
  @DisplayName("3. application 不依赖 adapter / infrastructure 层")
  void applicationDoesNotDependOnAdapterOrInfrastructure() {
    noClasses().that().resideInAPackage(KERNEL_APPLICATION)
      .should().dependOnClassesThat().resideInAnyPackage(KERNEL_ADAPTER, KERNEL_INFRASTRUCTURE)
      .because("01-架构与依赖规则：application 仅依赖 api + domain + shared starter")
      .check(classes);
  }

  @Test
  @DisplayName("4. kernel 各层只通过 api/types 依赖 file/approval，不触及其实现层")
  void kernelDependsOnBusinessServicesOnlyThroughApiAndTypes() {
    noClasses().that().resideInAnyPackage(
        KERNEL_DOMAIN, KERNEL_API, KERNEL_APPLICATION, KERNEL_ADAPTER, KERNEL_INFRASTRUCTURE)
      .should().dependOnClassesThat().resideInAnyPackage(CROSS_SERVICE_IMPL_LAYERS)
      .because("01-架构与依赖规则：跨服务只依赖 API 模块（ID 原语 types 层亦允许）")
      .check(classes);
  }
}
