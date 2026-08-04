package com.example.shared.id.validator;

import com.example.shared.id.metadata.IdMetadataResolver;
import com.example.shared.id.properties.IdProperties;
import com.example.shared.identifier.contract.IdDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ID 定义启动校验器 (Refactored)
 * <p>
 * 职责：
 * 1. 扫描指定包下的 @IdDefinition 类
 * 2. 触发 IdMetadataResolver 进行预解析和校验
 * 3. 聚合所有校验错误，在启动时通过异常中断应用，防止脏配置上线
 */
@Slf4j
@RequiredArgsConstructor
public class IdDefinitionStartupValidator implements ApplicationRunner {

  private final IdProperties properties;
  private final IdMetadataResolver metadataResolver;

  @Override
  public void run(ApplicationArguments args) {
    // 1. 前置检查：是否需要运行
    if (!shouldRun()) {
      return;
    }

    long startTime = System.currentTimeMillis();
    List<String> targetPackages = properties.getValidation().getPackages();
    log.info("[ID-Validator] Starting validation for packages: {}", targetPackages);

    // 2. 发现候选类 (Scanning)
    Set<String> candidateClassNames = scanCandidateClasses(targetPackages);
    if (candidateClassNames.isEmpty()) {
      log.warn("[ID-Validator] No @IdDefinition classes found in configured packages.");
      return;
    }

    // 3. 执行校验 (Validation & Error Aggregation)
    List<String> validationErrors = validateClasses(candidateClassNames);

    // 4. 结果处理 (Reporting)
    handleValidationResult(candidateClassNames.size(), validationErrors, startTime);
  }

  // =========================================================
  // 1. 配置检查
  // =========================================================
  private boolean shouldRun() {
    if (!properties.getValidation().isEnabled()) {
      log.debug("[ID-Validator] Validation is disabled.");
      return false;
    }
    if (CollectionUtils.isEmpty(properties.getValidation().getPackages())) {
      log.warn("[ID-Validator] Validation enabled but 'shared.identity.validation.packages' is empty.");
      return false;
    }
    return true;
  }

  // =========================================================
  // 2. 类扫描 (Discovery)
  // =========================================================
  private Set<String> scanCandidateClasses(List<String> basePackages) {
    ClassPathScanningCandidateComponentProvider scanner = createScanner();
    Set<String> classNames = new HashSet<>();

    for (String basePackage : basePackages) {
      try {
        Set<BeanDefinition> candidates = scanner.findCandidateComponents(basePackage);
        for (BeanDefinition candidate : candidates) {
          classNames.add(candidate.getBeanClassName());
        }
      } catch (Exception e) {
        log.error("[ID-Validator] Failed to scan package: {}", basePackage, e);
        // 扫描失败属于严重配置错误，直接抛出
        throw new IllegalStateException("Failed to scan package for ID definitions: " + basePackage, e);
      }
    }
    return classNames;
  }

  private ClassPathScanningCandidateComponentProvider createScanner() {
    // false: 不使用默认过滤器（默认会扫描 @Component 等）
    ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
    // 只包含 @IdDefinition 注解
    scanner.addIncludeFilter(new AnnotationTypeFilter(IdDefinition.class));
    return scanner;
  }

  // =========================================================
  // 3. 核心校验逻辑 (Validation)
  // =========================================================
  private List<String> validateClasses(Set<String> classNames) {
    List<String> errors = new ArrayList<>();

    for (String className : classNames) {
      try {
        validateSingleClass(className);
      } catch (Throwable ex) {
        // 捕获所有异常，收集错误信息，而不是立即中断
        String errorMsg = String.format("Class [%s]: %s", className, ex.getMessage());
        errors.add(errorMsg);
        log.error("[ID-Validator] Check failed: {}", errorMsg);
      }
    }
    return errors;
  }

  private void validateSingleClass(String className) throws ClassNotFoundException {
    Class<?> clazz = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
    // 核心：调用 Resolver 触发解析和校验逻辑
    metadataResolver.resolve(clazz);
  }

  // =========================================================
  // 4. 结果报告 (Result Handling)
  // =========================================================
  private void handleValidationResult(int totalChecked, List<String> errors, long startTime) {
    long duration = System.currentTimeMillis() - startTime;

    if (errors.isEmpty()) {
      log.info("[ID-Validator] Validation PASSED. Checked {} classes in {} ms.", totalChecked, duration);
    } else {
      // 格式化错误报告，使其在控制台醒目显示
      StringBuilder sb = new StringBuilder();
      sb.append("\n========================================\n");
      sb.append("      ID DEFINITION VALIDATION FAILED      \n");
      sb.append("========================================\n");
      sb.append("Found ").append(errors.size()).append(" error(s) in ID configurations:\n");

      for (int i = 0; i < errors.size(); i++) {
        sb.append(i + 1).append(". ").append(errors.get(i)).append("\n");
      }
      sb.append("========================================\n");
      sb.append("Please fix the @IdDefinition configurations above.");

      // 抛出异常，阻止 Spring Boot 启动
      throw new IllegalStateException(sb.toString());
    }
  }
}
