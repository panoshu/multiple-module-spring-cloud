package com.example.annuity.application.service;

import com.example.annuity.api.dto.HealthResponse;
import com.example.annuity.api.dto.LinkApprovalRequest;
import com.example.annuity.api.dto.LinkFileRequest;
import com.example.annuity.api.dto.LinkIntegrationRequest;
import com.example.approval.api.ApprovalInstanceApi;
import com.example.approval.api.request.ListMyPendingApprovalsRequest;
import com.example.file.api.FileTaskApi;
import com.example.file.api.request.GetFileTaskRequest;
import com.example.integration.api.core.trade.api.TradeQueryApi;
import com.example.integration.api.core.trade.dto.PortfolioQueryDTO;
import com.example.shared.primitives.page.Pagination;
import com.example.shared.web.core.api.ApiResult;
import com.example.shared.web.core.dto.PageQuery;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 跨服务链路调用测试应用服务
 * <p>
 * 通过 @HttpExchange 客户端代理调用外部服务，验证服务间调用链路连通性。
 * 不包含业务逻辑，仅用于基础设施链路验证。
 *
 * @author annuity-service
 * @since 2026/7/23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LinkTestService {

  private final ApprovalInstanceApi approvalInstanceApi;
  private final FileTaskApi fileTaskApi;
  private final TradeQueryApi tradeQueryApi;

  /**
   * 健康检查
   */
  public ApiResult<HealthResponse> health() {
    var response = new HealthResponse("annuity-service", "UP", LocalDateTime.now().toString());
    return ApiResult.success(response);
  }

  /**
   * 跨服务调用审批服务：查询待审批列表
   */
  public ApiResult<Object> linkApproval(LinkApprovalRequest request) {
    log.info("链路测试: annuity -> approval, approver={}", request.approver());
    var approvalRequest = new ListMyPendingApprovalsRequest(
        request.approver(),
        Pagination.of(0, 10)
    );
    ApiResult<?> result = approvalInstanceApi.listMyPending(approvalRequest);
    log.info("链路测试: annuity -> approval 完成, code={}", result.code());
    return ApiResult.success(result.data());
  }

  /**
   * 跨服务调用文件服务：查询文件任务
   */
  public ApiResult<Object> linkFile(LinkFileRequest request) {
    log.info("链路测试: annuity -> file, fileTaskId={}", request.fileTaskId());
    var fileRequest = new GetFileTaskRequest(request.fileTaskId());
    ApiResult<?> result = fileTaskApi.get(fileRequest);
    log.info("链路测试: annuity -> file 完成, code={}", result.code());
    return ApiResult.success(result.data());
  }

  /**
   * 跨服务调用外接集成服务：查询投资组合余额
   */
  public ApiResult<Object> linkIntegration(LinkIntegrationRequest request) {
    log.info("链路测试: annuity -> integration, enterpriseCustomerNo={}", request.enterpriseCustomerNo());
    var queryDTO = new PortfolioQueryDTO(
        request.channel(), request.tellerNo(), request.tellerName(),
        request.enterpriseCustomerNo(), request.enterprisePlanNo(),
        request.annuityProductNo(), null,
        PageQuery.firstPage(10)
    );
    ApiResult<?> result = tradeQueryApi.queryBalance5564(queryDTO);
    log.info("链路测试: annuity -> integration 完成, code={}", result.code());
    return ApiResult.success(result.data());
  }
}
