package com.example.shared.permission;

import com.example.shared.security.permission.PermissionRegistrationPort;
import com.example.shared.security.annotation.RequirePermission;
import com.example.shared.security.permission.PermissionRegistrationRequest;
import com.example.shared.security.permission.PermissionItemDescriptor;
import com.example.shared.security.permission.PermissionRegistrationResponse;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 业务服务启动时上报权限点 Runner.
 *
 * <p>装配条件：
 * <ul>
 *   <li>{@code @ConditionalOnBean(PermissionRegistrationPort.class)} - 业务服务配置了 httpexchange 客户端</li>
 *   <li>{@code @ConditionalOnExpression} 排除 auth-service 自身</li>
 * </ul>
 *
 * <p>fail-soft：上报失败只记录 WARN 日志，不阻断业务服务启动。
 *
 * @author shared-permission-starter
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(PermissionRegistrationPort.class)
@ConditionalOnExpression("'${spring.application.name}' != 'auth-service'")
public class PermissionRegistrationRunner implements ApplicationRunner {

  private final RequestMappingHandlerMapping handlerMapping;
  private final PermissionRegistrationPort registrationApi;
  private final Environment environment;

  @Override
  public void run(ApplicationArguments args) {
    registerNow();
  }

  /**
   * 执行一次权限点上报（幂等：auth 侧 upsert，内容不变则 no-op）.
   *
   * <p>除启动时 {@link #run(ApplicationArguments)} 调用外，
   * {@link PermissionRegistrationRefresher}（TTL 心跳）定时重复调用——
   * 重注册即续期，防止服务长期运行后 auth 侧注册表陈旧。
   */
  public void registerNow() {
    String serviceName = environment.getProperty("spring.application.name", "unknown");

    try {
      List<PermissionItemDescriptor> descriptors = extractDescriptors();
      if (descriptors.isEmpty()) {
        log.debug("[PermissionRegistration] 服务 {} 无 @RequirePermission 注解，跳过上报", serviceName);
        return;
      }

      log.info("[PermissionRegistration] 服务 {} 开始上报 {} 个权限点", serviceName, descriptors.size());
      ApiResult<PermissionRegistrationResponse> result = registrationApi.register(
        new PermissionRegistrationRequest(serviceName, descriptors));

      if (result != null && result.isSuccess()) {
        PermissionRegistrationResponse data = result.data();
        if (data != null) {
          log.info("[PermissionRegistration] 服务 {} 上报完成: 接收 {}, 新增/更新 {}, 未变化 {}",
            serviceName, data.totalReceived(), data.upserted(), data.unchanged());
        }
      } else {
        log.warn("[PermissionRegistration] 服务 {} 上报失败: {} - {}",
          serviceName,
          result != null ? result.code() : "null",
          result != null ? result.message() : "响应为空");
      }
    } catch (Exception e) {
      log.warn("[PermissionRegistration] 服务 {} 上报权限点失败,不影响启动", serviceName, e);
    }
  }

  private List<PermissionItemDescriptor> extractDescriptors() {
    List<PermissionItemDescriptor> descriptors = new ArrayList<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
      RequirePermission annotation = AnnotationUtils.findAnnotation(
        entry.getValue().getMethod(), RequirePermission.class);
      if (annotation == null) {
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
}
