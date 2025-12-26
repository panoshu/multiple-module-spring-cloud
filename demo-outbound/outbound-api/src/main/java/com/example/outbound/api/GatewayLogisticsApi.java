package com.example.outbound.api;

import com.example.outbound.dto.logistics.LogisticsDTO;
import com.example.outbound.dto.logistics.LogisticsQueryCommand;
import com.example.shared.core.api.Result;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * GatewayLogisticsApi
 *
 * @author YourName
 * @since 2025/12/14 21:43
 */
@HttpExchange("logistics")
public interface GatewayLogisticsApi {

  @PostExchange("/query")
  Result<LogisticsDTO> query(@RequestBody LogisticsQueryCommand command);
}
