package com.example.annuity.api;

import com.example.annuity.api.dto.HealthResponse;
import com.example.annuity.api.dto.LinkApprovalRequest;
import com.example.annuity.api.dto.LinkFileRequest;
import com.example.annuity.api.dto.LinkIntegrationRequest;
import com.example.shared.web.core.api.ApiResult;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * 跨服务链路调用案例 API
 * <p>
 * 用于验证以下调用链路连通性：
 * <ul>
 *   <li>网关 → annuity-service（health）</li>
 *   <li>网关 → annuity-service → approval-service（link-approval）</li>
 *   <li>网关 → annuity-service → file-service（link-file）</li>
 *   <li>网关 → annuity-service → integration-service（link-integration）</li>
 * </ul>
 * 跨服务调用结果以 {@link Object} 透传返回，避免 annuity-api 耦合外部服务 DTO。
 *
 * @author annuity-service
 * @since 2026/7/23
 */
@HttpExchange("/api/annuity/test")
public interface AnnuityLinkTestApi {

  /**
   * 健康检查：验证 网关 → annuity-service 链路
   */
  @PostExchange("/health")
  ApiResult<HealthResponse> health();

  /**
   * 跨服务调用审批服务：验证 网关 → annuity-service → approval-service 链路
   */
  @PostExchange("/link-approval")
  ApiResult<Object> linkApproval(@Valid @RequestBody LinkApprovalRequest request);

  /**
   * 跨服务调用文件服务：验证 网关 → annuity-service → file-service 链路
   */
  @PostExchange("/link-file")
  ApiResult<Object> linkFile(@Valid @RequestBody LinkFileRequest request);

  /**
   * 跨服务调用外接集成服务：验证 网关 → annuity-service → integration-service 链路
   */
  @PostExchange("/link-integration")
  ApiResult<Object> linkIntegration(@Valid @RequestBody LinkIntegrationRequest request);
}
