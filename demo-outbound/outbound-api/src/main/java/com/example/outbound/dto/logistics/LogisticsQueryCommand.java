package com.example.outbound.dto.logistics;

import lombok.Data;

/**
 * LogisticsQueryCommand
 *
 * @author YourName
 * @since 2025/12/14 21:40
 */
@Data
public class LogisticsQueryCommand {
  private String trackingNo; // 运单号
  private String phone;      // 手机尾号（顺丰查询需要）

  /**
   * 新增渠道字段：SF, YTO
   */
  private String channel;
}
