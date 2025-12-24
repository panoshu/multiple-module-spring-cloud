package com.example.outbound.server.interfaces;

import com.example.outbound.api.GatewayLogisticsApi;
import com.example.outbound.dto.logistics.LogisticsDTO;
import com.example.outbound.dto.logistics.LogisticsQueryCommand;
import com.example.outbound.server.application.LogisticsApplicationService;
import com.example.shared.core.api.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * LogisticsController
 *
 * @author YourName
 * @since 2025/12/14 21:42
 */
@RestController
@RequiredArgsConstructor
public class LogisticsController implements GatewayLogisticsApi {

  private final LogisticsApplicationService logisticsService;

  @Override
  public Result<LogisticsDTO> query(@RequestBody LogisticsQueryCommand command) {
    // 1. 调用 Application Service
    LogisticsDTO data = logisticsService.queryLogistics(command);

    // 2. 包装 Result
    return Result.success(data);
  }
}
