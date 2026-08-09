package com.pension.permission.infrastructure.permission;

import com.example.auth.api.annotation.RequirePermission;
import com.example.auth.api.dto.PermissionItemDescriptor;
import com.example.shared.identifier.id.UserNo;
import com.pension.permission.domain.authorization.enumeration.PermissionCategory;
import com.pension.permission.domain.permission.aggregate.PermissionItem;
import com.pension.permission.domain.permission.enumeration.PermissionItemSource;
import com.pension.permission.domain.permission.repository.PermissionItemRepository;
import com.pension.permission.types.PermissionItemId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限点扫描服务.
 *
 * <p>封装 {@link PermissionItem} 的扫描与上报逻辑，供 auth-infrastructure 的
 * {@code PermissionScanner}（启动扫描）和 auth-adapter 的 {@code PermissionRegistrationController}（外部上报）共用。
 *
 * <p>两类入口：
 * <ul>
 *   <li>{@link #scanLocal} - auth-service 启动时扫描本地 Controller</li>
 *   <li>{@link #registerFromExternal} - 业务服务通过 HttpExchange 上报</li>
 * </ul>
 *
 * @author auth-infrastructure
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionScannerService {

  private final PermissionItemRepository repository;

  /**
   * 扫描本地 Controller（auth-service 自身的权限点）.
   *
   * @param handlerMapping Spring MVC 请求映射
   * @param scanner        扫描者标识
   * @return 扫描结果
   */
  public ScanResult scanLocal(RequestMappingHandlerMapping handlerMapping, UserNo scanner) {
    List<PermissionItemDescriptor> descriptors = extractDescriptors(handlerMapping);
    List<PermissionItem> items = descriptors.stream()
      .map(d -> toItem(d, scanner))
      .toList();

    int upserted = repository.upsertAll(items, scanner);

    Set<PermissionItemId> scannedIds = items.stream()
      .map(item -> repository.findByBusinessAndAction(item.businessCode(), item.actionCode())
        .map(PermissionItem::id).orElse(null))
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
    repository.markStaleForUnscanned(scannedIds, scanner);

    return new ScanResult(items.size(), upserted, items.size() - upserted);
  }

  /**
   * 注册外部业务服务上报的权限点.
   *
   * <p>不执行 markStaleForUnscanned，避免业务服务之间互相影响。
   *
   * @param sourceService 来源服务名
   * @param items         权限点描述符列表
   * @return 上报结果
   */
  public PermissionRegistrationResult registerFromExternal(
    String sourceService, List<PermissionItemDescriptor> items) {
    UserNo scanner = UserNo.of("scanner:" + sourceService);
    List<PermissionItem> permissionItems = items.stream()
      .map(d -> toItem(d, scanner))
      .toList();
    int upserted = repository.upsertAll(permissionItems, scanner);
    return new PermissionRegistrationResult(items.size(), upserted, items.size() - upserted);
  }

  private List<PermissionItemDescriptor> extractDescriptors(RequestMappingHandlerMapping handlerMapping) {
    List<PermissionItemDescriptor> descriptors = new ArrayList<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
      RequirePermission annotation = AnnotationUtils.findAnnotation(
        entry.getValue().getMethod(), RequirePermission.class);
      if (annotation == null) {
        logUnannotated(entry.getValue());
        continue;
      }

      String path = extractPath(entry.getKey());
      String httpMethod = extractHttpMethod(entry.getKey());
      String action = annotation.action().isEmpty() ? null : annotation.action();

      descriptors.add(new PermissionItemDescriptor(
        annotation.business(),
        action,
        annotation.category().name(),
        entry.getValue().getBeanType().getSimpleName(),
        entry.getValue().getMethod().getName(),
        httpMethod,
        path));
    }
    return descriptors;
  }

  private PermissionItem toItem(PermissionItemDescriptor d, UserNo scanner) {
    PermissionCategory category = PermissionCategory.valueOf(d.category());
    return PermissionItem.create(
      d.businessCode(),
      d.actionCode(),
      category,
      PermissionItemSource.API,
      d.controller(),
      d.method(),
      d.httpMethod(),
      d.path(),
      scanner);
  }

  private String extractPath(RequestMappingInfo info) {
    if (info.getPathPatternsCondition() != null) {
      return String.join(",", info.getPathPatternsCondition().getPatternValues());
    }
    if (info.getPatternsCondition() != null) {
      return String.join(",", info.getPatternsCondition().getPatterns());
    }
    return null;
  }

  private String extractHttpMethod(RequestMappingInfo info) {
    if (info.getMethodsCondition() == null) {
      return null;
    }
    return info.getMethodsCondition().getMethods().stream()
      .map(Enum::name)
      .reduce((a, b) -> a + "," + b)
      .orElse(null);
  }

  private void logUnannotated(HandlerMethod handlerMethod) {
    log.warn("未声明 @RequirePermission 的接口: {}.{}",
      handlerMethod.getBeanType().getSimpleName(),
      handlerMethod.getMethod().getName());
  }
}
