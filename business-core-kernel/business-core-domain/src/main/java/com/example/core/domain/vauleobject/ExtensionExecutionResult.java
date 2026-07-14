package com.example.core.domain.vauleobject;

import java.util.Map;

/**
 * 扩展动作执行结果：支持传递上下文突变 (Mutations)
 *
 * @author <a href="mailto: admin@panoshu.top">panoshu</a>
 * @since 2026/5/16 20:58
 */
public record ExtensionExecutionResult(
  boolean isSuccess,
  String errorCode,
  String errorMessage,
  int totalScanned,   // 总扫描数
  int totalProcessed, // 符合条件且执行数
  int totalSkipped,   // 被条件跳过数
  int totalFailed,    // 执行异常数
  Map<String, Object> mutations // 产生的新事实，供后续动作或主Handler使用
) {
  // 快捷构造：成功(无指标)
  public static ExtensionExecutionResult success() {
    return new ExtensionExecutionResult(true, null, null, 1, 1, 0, 0, null);
  }

  public static ExtensionExecutionResult skip() {
    return new ExtensionExecutionResult(true, null, null, 1, 0, 1, 0, null);
  }

  // 快捷构造：成功(带突变事实，一般用于计划层 Enrichment)
  public static ExtensionExecutionResult success(Map<String, Object> mutations) {
    return new ExtensionExecutionResult(true, null, null, 1, 1, 0, 0, mutations);
  }

  // 快捷构造：成功(带明细指标，由明细层基类使用)
  public static ExtensionExecutionResult success(int scanned, int processed, int skipped, int failed) {
    return new ExtensionExecutionResult(true, null, null, scanned, processed, skipped, failed, null);
  }

  // 快捷构造：失败
  public static ExtensionExecutionResult failure(String code, String msg) {
    return new ExtensionExecutionResult(false, code, msg, 1, 0, 0, 1, null);
  }
}
