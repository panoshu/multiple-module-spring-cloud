package com.example.annuity.adapter.controller;

import com.example.annuity.api.AnnuityLinkTestApi;
import com.example.annuity.api.dto.HealthResponse;
import com.example.annuity.api.dto.LinkApprovalRequest;
import com.example.annuity.api.dto.LinkFileRequest;
import com.example.annuity.api.dto.LinkIntegrationRequest;
import com.example.annuity.application.service.LinkTestService;
import com.example.shared.web.core.api.ApiResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 跨服务链路调用案例 Controller
 * <p>
 * 实现 {@link AnnuityLinkTestApi}，委托 {@link LinkTestService} 完成跨服务调用，
 * 验证 网关 → annuity-service → {approval/file/integration} 链路连通性。
 *
 * @author annuity-service
 * @since 2026/7/23
 */
@Slf4j
@RestController
@RequestMapping("/api/annuity/test")
@RequiredArgsConstructor
public class AnnuityLinkTestController implements AnnuityLinkTestApi {

  private final LinkTestService linkTestService;

  @Override
  public ApiResult<HealthResponse> health() {
    return linkTestService.health();
  }

  @Override
  public ApiResult<Object> linkApproval(LinkApprovalRequest request) {
    return linkTestService.linkApproval(request);
  }

  @Override
  public ApiResult<Object> linkFile(LinkFileRequest request) {
    return linkTestService.linkFile(request);
  }

  @Override
  public ApiResult<Object> linkIntegration(LinkIntegrationRequest request) {
    return linkTestService.linkIntegration(request);
  }
}
