package core.domain.model;

public record InboundRule(
  HeaderZone headerZone,      // 头信息区域定义
  DetailZone detailZone       // 明细信息区域定义
) {
}
