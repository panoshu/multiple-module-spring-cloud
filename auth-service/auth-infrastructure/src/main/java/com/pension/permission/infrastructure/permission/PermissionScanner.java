package com.pension.permission.infrastructure.permission;

import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.permission.aggregate.PermissionItem;
import com.pension.permission.domain.permission.enumeration.PermissionItemSource;
import com.pension.permission.domain.permission.repository.PermissionItemRepository;
import com.pension.permission.sdk.RequirePermission;
import com.pension.permission.types.PermissionItemId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限点自动发现扫描器。
 * <p>启动时扫描所有 Controller 方法的 {@link RequirePermission} 注解，
 * upsert 到 {@code t_auth_permission_item} 表，并标记未扫描到的 autoRegistered=true 记录为 stale。
 * <p>未声明 {@code @RequirePermission} 的接口采用"告警不阻断"模式，
 * 输出未声明列表到启动日志，不阻断请求。后期通过配置切换为强制模式。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionScanner implements ApplicationRunner {

  private static final UserNo SCANNER_IDENTITY = UserNo.of("permission-scanner");

  private final PermissionItemRepository repository;
  private final RequestMappingHandlerMapping handlerMapping;

  @Override
  public void run(ApplicationArguments args) {
    scan(SCANNER_IDENTITY);
  }

  public void scan(UserNo scanner) {
    Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();

    List<PermissionItem> discovered = new ArrayList<>();
    Set<String> scannedKeys = new HashSet<>();
    int unannotatedCount = 0;

    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
      HandlerMethod handlerMethod = entry.getValue();
      RequirePermission annotation = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), RequirePermission.class);

      if (annotation == null) {
        unannotatedCount++;
        logUnannotated(handlerMethod);
        continue;
      }

      String path = extractPath(entry.getKey());
      String httpMethod = extractHttpMethod(entry.getKey());
      PermissionCategory category = mapCategory(annotation.category());
      String action = annotation.action().isEmpty() ? null : annotation.action();

      PermissionItem item = PermissionItem.create(
        annotation.business(), action, category,
        PermissionItemSource.API,
        handlerMethod.getBeanType().getSimpleName(),
        handlerMethod.getMethod().getName(),
        httpMethod, path, scanner);
      discovered.add(item);
      scannedKeys.add(item.businessCode().value() + "|"
        + (item.actionCode() != null ? item.actionCode().value() : ""));
    }

    repository.upsertAll(discovered, scanner);

    Set<PermissionItemId> scannedIds = new HashSet<>();
    for (PermissionItem persisted : repository.loadAllItems()) {
      String key = persisted.businessCode().value() + "|"
        + (persisted.actionCode() != null ? persisted.actionCode().value() : "");
      if (scannedKeys.contains(key)) {
        scannedIds.add(persisted.id());
      }
    }
    repository.markStaleForUnscanned(scannedIds, scanner);

    log.info("权限点扫描完成：发现 {} 个，未声明注解接口 {} 个", discovered.size(), unannotatedCount);
  }

  private PermissionCategory mapCategory(com.pension.permission.sdk.PermissionCategory sdkCategory) {
    return PermissionCategory.valueOf(sdkCategory.name());
  }

  private String extractPath(RequestMappingInfo info) {
    if (info.getPatternsCondition() != null) {
      return String.join(",", info.getPatternsCondition().getPatterns());
    }
    if (info.getPathPatternsCondition() != null) {
      return String.join(",", info.getPathPatternsCondition().getPatternValues());
    }
    return null;
  }

  private String extractHttpMethod(RequestMappingInfo info) {
    if (info.getMethodsCondition() == null) {
      return null;
    }
    return info.getMethodsCondition().getMethods().stream()
      .map(m -> m.name())
      .reduce((a, b) -> a + "," + b)
      .orElse(null);
  }

  private void logUnannotated(HandlerMethod handlerMethod) {
    log.warn("未声明 @RequirePermission 的接口: {}.{}",
      handlerMethod.getBeanType().getSimpleName(),
      handlerMethod.getMethod().getName());
  }
}
