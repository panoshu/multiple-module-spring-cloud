package com.example.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * §21.4 向前约束（批次 50，对应规格修订 §23.56）。
 * <p>
 * 规格修订后明确「存量不回溯」，故本类只约束<b>新增</b>：以「存量方法基线快照」豁免现有裸参方法，
 * 任何不在基线中的新 public 方法，其参数必须是 CQE（Command/Query/Event）或零参；types 层不得再
 * 放入聚合根/实体/领域服务。基线按 {@code 简单类名#方法名} 记录，参数类型重构不会误报，新增方法名
 * 则必然被检查。防假绿：断言导入规模、Service 类数、types 类数，以及基线确实命中导入类的方法。
 */
@DisplayName("§21.4 向前约束（批次 50，存量不回溯）")
class KernelForwardConstraintTest {

  private static final String KERNEL_APPLICATION = "com.example.core.application..";
  private static final String KERNEL_TYPES = "com.example.core.types..";
  private static final String ENTITY_FQN = "com.example.shared.domain.aggregate.entity.Entity";
  private static final String DOMAIN_SERVICE_FQN = "com.example.shared.domain.annotation.DomainService";

  /** 批次 50 时点的存量方法基线快照。新增方法不在其中，须用 CQE 或零参。 */
  private static final Set<String> LEGACY_METHODS = Set.of(
    "BusinessApplicationAppService#advanceApplication",
    "BusinessApplicationAppService#advanceStep",
    "BusinessApplicationAppService#findByBatchId",
    "BusinessApplicationAppService#loadOrThrow",
    "BusinessApplicationAppService#submit",
    "BusinessBatchAppService#cancel",
    "BusinessBatchAppService#createBatch",
    "BusinessBatchAppService#findActive",
    "BusinessBatchAppService#findByFormId",
    "BusinessBatchAppService#loadOrThrow",
    "BusinessBatchAppService#reconcileAllActive",
    "BusinessBatchAppService#reconcileStatistics",
    "BusinessFormAppService#applyUploadToken",
    "BusinessFormAppService#confirmUpload",
    "BusinessFormAppService#deleteForm",
    "BusinessFormAppService#getFormStatus",
    "BusinessFormAppService#handleParsingResult",
    "BusinessFormAppService#triggerParsing",
    "BusinessProgressAppService#getBatchProgress",
    "CallbackCorrelationService#correlate",
    "ExternalInteractionRecoveryService#claimPendingDispatch",
    "ExternalInteractionRecoveryService#processDueRetries",
    "ExternalInteractionRecoveryService#reclaimStaleDispatching",
    "ExternalInteractionRecoveryService#reconcileUnknown",
    "FlowOrchestrationService#advanceApplication",
    "FlowOrchestrationService#advanceStep",
    "FlowOrchestrationService#advanceByApprovalResult",
    "FlowOrchestrationService#advanceByFileTaskId",
    "FlowOrchestrationService#completeWaitingStep",
    "FlowOrchestrationService#isSystemTask",
    "MaterialAppService#bindIndividualMaterial",
    "MaterialAppService#bindPackageMaterials",
    "MaterialAppService#checkCompleteness",
    "MaterialAppService#isSatisfied",
    "MaterialAppService#listMaterials",
    "MaterialAppService#unbindIndividualMaterial",
    "ProcessDefinitionPublishService#publish"
  );

  private static JavaClasses classes;

  @BeforeAll
  static void importKernelLayers() {
    classes = new ClassFileImporter().importPackages(
      "com.example.core", "com.example.shared", "com.example.file", "com.example.approval");
    // 防假绿：确认被检查的包确实导入了类
    assertThat(classes.size()).isGreaterThan(500);
    assertThat(countServiceClasses()).isGreaterThanOrEqualTo(9L);
    assertThat(countIn("com.example.core.types")).isGreaterThan(10L);
    // 防假绿：基线必须真实命中导入类的方法，避免白名单写错导致规则空转
    assertThat(matchedLegacyMethodCount()).isGreaterThan(20L);
  }

  @Test
  @DisplayName("1. 新增 application *Service public 方法参数须为 CQE 或零参")
  void newServiceMethods_useCqeOrNoArgs() {
    classes().that().resideInAPackage(KERNEL_APPLICATION)
      .and().haveSimpleNameEndingWith("Service")
      .should(NEW_METHODS_USE_CQE)
      .because("§21.4（批次 49 修订）：新增/修改的写操作入口必须 CQE，存量方法见基线快照")
      .check(classes);
  }

  @Test
  @DisplayName("2. types 层不得放聚合根/实体/领域服务")
  void typesLayer_onlySharedPrimitives() {
    noClasses().that().resideInAPackage(KERNEL_TYPES)
      .should().beAssignableTo(ENTITY_FQN)
      .because("§21.4（批次 49 修订）：types 只放跨层共享领域原语，不得放聚合根/实体")
      .check(classes);
    noClasses().that().resideInAPackage(KERNEL_TYPES)
      .should().beAnnotatedWith(DOMAIN_SERVICE_FQN)
      .because("§21.4（批次 49 修订）：types 不得放领域服务")
      .check(classes);
  }

  private static final ArchCondition<JavaClass> NEW_METHODS_USE_CQE =
    new ArchCondition<>("use CQE or no args for methods outside the legacy baseline") {
      @Override
      public void check(JavaClass clazz, ConditionEvents events) {
        for (JavaMethod method : clazz.getMethods()) {
          if (method.isConstructor() || !method.getModifiers().contains(JavaModifier.PUBLIC)) {
            continue;
          }
          String key = clazz.getSimpleName() + "#" + method.getName();
          if (LEGACY_METHODS.contains(key)) {
            continue;
          }
          List<JavaClass> params = method.getRawParameterTypes();
          boolean compliant = params.isEmpty() || params.stream().allMatch(p -> {
            String name = p.getSimpleName();
            return name.endsWith("Command") || name.endsWith("Query") || name.endsWith("Event");
          });
          if (!compliant) {
            events.add(SimpleConditionEvent.violated(method,
              "新增方法 " + key + " 使用非 CQE 参数，应改用 Command/Query/Event，或若为存量则加入基线"));
          }
        }
      }
    };

  private static long countServiceClasses() {
    long count = 0;
    for (JavaClass clazz : classes) {
      if (clazz.getPackageName().startsWith("com.example.core.application")
        && clazz.getSimpleName().endsWith("Service")) {
        count++;
      }
    }
    return count;
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

  private static long matchedLegacyMethodCount() {
    Set<String> actual = new HashSet<>();
    for (JavaClass clazz : classes) {
      if (clazz.getPackageName().startsWith("com.example.core.application")
        && clazz.getSimpleName().endsWith("Service")) {
        for (JavaMethod method : clazz.getMethods()) {
          actual.add(clazz.getSimpleName() + "#" + method.getName());
        }
      }
    }
    return LEGACY_METHODS.stream().filter(actual::contains).count();
  }
}
