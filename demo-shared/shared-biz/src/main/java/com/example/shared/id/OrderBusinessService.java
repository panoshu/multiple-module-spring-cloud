package com.example.shared.id;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderBusinessService {

  // 1. 直接注入 Core 模块提供的 Bean
  private final DistributedIdGenerator idGenerator;

  public OrderBusinessService(DistributedIdGenerator idGenerator) {
    this.idGenerator = idGenerator;
  }

  /**
   * 场景：用户下单，创建一笔交易提交，并拆分为多个子单
   */
  @Transactional
  public void createOrder(String custNo, List<String> products) {
    String bizCode = "SHOP";

    // ============================================
    // 步骤 A: 生成 Submission ID (有状态，会查库/缓存)
    // ============================================
    // 结果示例: SHOPCUST001202511240001
    String submissionId = idGenerator.nextSubmissionId(bizCode, custNo);
    System.out.println("交易提交号: " + submissionId);

    // 保存交易主单逻辑...
    // saveTrade(submissionId, ...);

    // ============================================
    // 步骤 B: 派生 Order ID (无状态，纯内存)
    // ============================================
    int index = 1;
    for (String prod : products) {
      // 结果示例: SHOPCUST00120251124000101, ...02
      String subOrderId = idGenerator.deriveOrderId(submissionId, index++);

      System.out.println("  └─ 子单号: " + subOrderId);

      // 保存子单逻辑...
      // saveSubOrder(subOrderId, prod);
    }
  }
}
