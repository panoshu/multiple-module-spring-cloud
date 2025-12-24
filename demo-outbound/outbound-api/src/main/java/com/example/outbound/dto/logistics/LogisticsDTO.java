package com.example.outbound.dto.logistics;

import java.io.Serializable;
import java.util.List;

/**
 * LogisticsDTO
 *
 * @author YourName
 * @since 2025/12/14 21:41
 */
public record LogisticsDTO(
  String trackingNo,
  String currentStatus, // 这里返回的是字符串描述，而不是 Enums
  List<Node> history
) implements Serializable {

  /**
   * 内部嵌套 Record，对应物流轨迹节点
   */
  public record Node(
    String time,
    String description
  ) implements Serializable {}
}
