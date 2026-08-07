package com.example.shared.permission;

/**
 * 请求 DTO 实现此接口后，{@link RequirePermissionAspect} 切面会自动从方法入参中
 * 解析出 planId，供权限校验使用。
 *
 * <p>业务服务的请求 DTO 实现此接口，切面通过它解析 planId：
 * <pre>{@code
 * public class CreateApprovalFlowRequest implements PlanIdAware {
 *     private String planId;
 *     private String flowName;
 *
 *     @Override
 *     public String planId() { return planId; }
 * }
 * }</pre>
 *
 * <p>平台类权限（{@code category = PLATFORM}）不需要 planId，DTO 可不实现此接口。
 *
 * @author shared-permission-starter
 */
public interface PlanIdAware {

  /**
   * 返回当前请求关联的计划编号，null 表示无关联计划（平台类权限）。
   */
  String planId();
}
