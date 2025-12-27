package com.example.outbound.server.application;

import com.example.outbound.dto.logistics.LogisticsDTO;
import com.example.outbound.dto.logistics.LogisticsQueryCommand;
import com.example.outbound.dto.logistics.SmartRouteCommand;
import com.example.outbound.server.domain.logistics.CargoType;
import com.example.outbound.server.domain.logistics.CarrierSelectionPolicy;
import com.example.outbound.server.domain.logistics.LogisticsGateway;
import com.example.outbound.server.domain.logistics.LogisticsInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * LogisticsApplicationService
 *
 * @author YourName
 * @since 2025/12/14 21:42
 */
@Service
@RequiredArgsConstructor
public class LogisticsApplicationService {

  private final CarrierSelectionPolicy carrierSelectionPolicy;

  // 依赖倒置：只依赖 Domain 接口，不依赖 Infra 实现
  private final List<LogisticsGateway> gateways;

  public LogisticsDTO queryLogistics(LogisticsQueryCommand command) {
    // 1. 简单的入参校验 (复杂的业务校验放在 Domain)
    if (command.getTrackingNo() == null) {
      throw new IllegalArgumentException("运单号不能为空");
    }

    LogisticsGateway gateway = gateways.stream()
      .filter(g -> g.supports(command.getChannel()))
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException("不支持的物流渠道: " + command.getChannel()));

    // 2. 调用选中的实现
    LogisticsInfo info = gateway.getLogisticsByPhone(command.getPhone());

    // 3. 模型转换: Domain Model -> API DTO
    // 为什么要做这一步？因为 Domain Model 可能包含一些敏感字段或业务计算字段，
    // 而 API DTO 是给外部看的视图。
    return toDto(info);
  }

  public LogisticsDTO smartRoute(SmartRouteCommand cmd) {
    // 1. 将 DTO 的 String 类型转为 领域的 Enum
    CargoType cargoType = CargoType.resolve(cmd.type());

    // 2. 领域层决策
    String channel = carrierSelectionPolicy.selectChannel(cmd.weight(), cargoType);

    // 3. 寻找适配器
    LogisticsGateway gateway = gateways.stream()
      .filter(g -> g.supports(channel))
      .findFirst()
      .orElseThrow(() -> new RuntimeException("No route found"));

    // 4. 获取领域对象
    LogisticsInfo info = gateway.getLogisticsByTrackingNo(cmd.trackingNo());

    // 5. 如果需要拦截，执行领域行为
    if (cmd.needIntercept()) {
      info.checkInterceptable();
      // calling gateway.intercept(...) logic here
    }

    // 6. 模型转换 (Domain -> DTO)
    return toDto(info);
  }

  private LogisticsDTO toDto(LogisticsInfo info) {
    // 转换节点列表
    List<LogisticsDTO.Node> nodeDtos = info.nodes().stream() // 假设 Domain Entity 有 getNodes()
      .map(n -> new LogisticsDTO.Node(n.time(), n.desc()))
      .toList(); // JDK 16+ 直接用 toList()

    // 组装 DTO Record
    return new LogisticsDTO(
      info.channel(),

      // 关键点：将领域枚举转换为前端可读的字符串
      // 例如：LogisticsStatus.TRANSPORTING -> "运输中" 或 "TRANSPORTING"
      info.status().name(),

      nodeDtos
    );
  }
}
